/*
 * Copyright (C) 2006 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.workflow.support.concurrent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;

/**
   * Class that implements {@link #allAsList} and {@link #successfulAsList}.
   * The idea is to create a (null-filled) List and register a listener with
   * each component future to fill out the value in the List when that future
   * completes.
   */
class ListFuture<V> extends AbstractFuture<List<V>> {
    ImmutableList<? extends ListenableFuture<? extends V>> futures;
    final boolean allMustSucceed;
    final AtomicInteger remaining;
    List<V> values;

    /**
     * Constructor.
     *
     * @param futures all the futures to build the list from
     * @param allMustSucceed whether a single failure or cancellation should
     *        propagate to this future
     * @param listenerExecutor used to run listeners on all the passed in
     *        futures.
     */
    ListFuture(
        final ImmutableList<? extends ListenableFuture<? extends V>> futures,
        final boolean allMustSucceed, final Executor listenerExecutor) {
      this.futures = futures;
      this.values = Lists.newArrayListWithCapacity(futures.size());
      this.allMustSucceed = allMustSucceed;
      this.remaining = new AtomicInteger(futures.size());

      init(listenerExecutor);
    }

    private void init(final Executor listenerExecutor) {
      // First, schedule cleanup to execute when the Future is done.
      addListener(new Runnable() {
        @Override
        public void run() {
          // By now the values array has either been set as the Future's value,
          // or (in case of failure) is no longer useful.
          ListFuture.this.values = null;

          // Let go of the memory held by other futures
          ListFuture.this.futures = null;
        }
      }, MoreExecutors.sameThreadExecutor());

      // Now begin the "real" initialization.

      // Corner case: List is empty.
      if (futures.isEmpty()) {
        set(Lists.newArrayList(values));
        return;
      }

      // Populate the results list with null initially.
      for (int i = 0; i < futures.size(); ++i) {
        values.add(null);
      }

      // Register a listener on each Future in the list to update
      // the state of this future.
      // Note that if all the futures on the list are done prior to completing
      // this loop, the last call to addListener() will callback to
      // setOneValue(), transitively call our cleanup listener, and set
      // this.futures to null.
      // We store a reference to futures to avoid the NPE.
      ImmutableList<? extends ListenableFuture<? extends V>> localFutures = futures;
      for (int i = 0; i < localFutures.size(); i++) {
        final ListenableFuture<? extends V> listenable = localFutures.get(i);
        final int index = i;
        listenable.addListener(new Runnable() {
          @Override
          public void run() {
            setOneValue(index, listenable);
          }
        }, listenerExecutor);
      }
    }

    /**
     * Sets the value at the given index to that of the given future.
     */
    private void setOneValue(int index, Future<? extends V> future) {
      List<V> localValues = values;
      if (isDone() || localValues == null) {
        // Some other future failed or has been cancelled, causing this one to
        // also be cancelled or have an exception set. This should only happen
        // if allMustSucceed is true.
        checkState(allMustSucceed,
            "Future was done before all dependencies completed");
        return;
      }

      try {
        checkState(future.isDone(),
            "Tried to set value from future which is not done");
        localValues.set(index, getUninterruptibly(future));
      } catch (CancellationException e) {
        if (allMustSucceed) {
          // Set ourselves as cancelled. Let the input futures keep running
          // as some of them may be used elsewhere.
          // (Currently we don't override interruptTask, so
          // mayInterruptIfRunning==false isn't technically necessary.)
          cancel(false);
        }
      } catch (ExecutionException e) {
        if (allMustSucceed) {
          // As soon as the first one fails, throw the exception up.
          // The result of all other inputs is then ignored.
          setException(e.getCause());
        }
      } catch (RuntimeException e) {
        if (allMustSucceed) {
          setException(e);
        }
      } catch (Error e) {
        // Propagate errors up ASAP - our superclass will rethrow the error
        setException(e);
      } finally {
        int newRemaining = remaining.decrementAndGet();
        checkState(newRemaining >= 0, "Less than 0 remaining futures");
        if (newRemaining == 0) {
          localValues = values;
          if (localValues != null) {
            set(Lists.newArrayList(localValues));
          } else {
            checkState(isDone());
          }
        }
      }
    }

    @Override
    public List<V> get() throws InterruptedException, ExecutionException {
      callAllGets();

      // This may still block in spite of the calls above, as the listeners may
      // be scheduled for execution in other threads.
      return super.get();
    }

    /**
     * Calls the get method of all dependency futures to work around a bug in
     * some ListenableFutures where the listeners aren't called until get() is
     * called.
     */
    private void callAllGets() throws InterruptedException {
      List<? extends ListenableFuture<? extends V>> oldFutures = futures;
      if (oldFutures != null && !isDone()) {
        for (ListenableFuture<? extends V> future : oldFutures) {
          // We wait for a little while for the future, but if it's not done,
          // we check that no other futures caused a cancellation or failure.
          // This can introduce a delay of up to 10ms in reporting an exception.
          while (!future.isDone()) {
            try {
              future.get();
            } catch (Error e) {
              throw e;
            } catch (InterruptedException e) {
              throw e;
            } catch (Throwable e) {
              // ExecutionException / CancellationException / RuntimeException
              if (allMustSucceed) {
                return;
              } else {
                continue;
              }
            }
          }
        }
      }
    }
  }

