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

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;

/**
 * Various convenience methods for working with {@link ListenableFuture}s.
 *
 * <p>
 * Mostly copied after Guava's {@code Futures}, because that one is still marked as beta
 * and is subject to change.
 *
 * @author Guava
 */
public abstract class Futures {
    /**
     * Registers separate success and failure callbacks to be run when the {@code
     * Future}'s computation is {@linkplain java.util.concurrent.Future#isDone()
     * complete} or, if the computation is already complete, immediately.
     *
     * <p>There is no guaranteed ordering of execution of callbacks, but any
     * callback added through this method is guaranteed to be called once the
     * computation is complete.
     *
     * Example: <pre> {@code
     * ListenableFuture<QueryResult> future = ...;
     * addCallback(future,
     *     new FutureCallback<QueryResult> {
     *       public void onSuccess(QueryResult result) {
     *         storeInCache(result);
     *       }
     *       public void onFailure(Throwable t) {
     *         reportError(t);
     *       }
     *     });}</pre>
     *
     * <p>Note: This overload of {@code addCallback} is designed for cases in
     * which the callack is fast and lightweight, as the method does not accept
     * an {@code Executor} in which to perform the the work. For heavier
     * callbacks, this overload carries some caveats: First, the thread that the
     * callback runs in depends on whether the input {@code Future} is done at the
     * time {@code addCallback} is called and on whether the input {@code Future}
     * is ever cancelled. In particular, {@code addCallback} may execute the
     * callback in the thread that calls {@code addCallback} or {@code
     * Future.cancel}. Second, callbacks may run in an internal thread of the
     * system responsible for the input {@code Future}, such as an RPC network
     * thread. Finally, during the execution of a {@code sameThreadExecutor}
     * callback, all other registered but unexecuted listeners are prevented from
     * running, even if those listeners are to run in other executors.
     *
     * <p>For a more general interface to attach a completion listener to a
     * {@code Future}, see {@link ListenableFuture#addListener addListener}.
     *
     * @param future The future attach the callback to.
     * @param callback The callback to invoke when {@code future} is completed.
     * @since 10.0
     */
    public static <V> void addCallback(ListenableFuture<V> future,
        FutureCallback<? super V> callback) {
      addCallback(future, callback, MoreExecutors.sameThreadExecutor());
    }

    /**
     * Registers separate success and failure callbacks to be run when the {@code
     * Future}'s computation is {@linkplain java.util.concurrent.Future#isDone()
     * complete} or, if the computation is already complete, immediately.
     *
     * <p>The callback is run in {@code executor}.
     * There is no guaranteed ordering of execution of callbacks, but any
     * callback added through this method is guaranteed to be called once the
     * computation is complete.
     *
     * Example: <pre> {@code
     * ListenableFuture<QueryResult> future = ...;
     * Executor e = ...
     * addCallback(future, e,
     *     new FutureCallback<QueryResult> {
     *       public void onSuccess(QueryResult result) {
     *         storeInCache(result);
     *       }
     *       public void onFailure(Throwable t) {
     *         reportError(t);
     *       }
     *     });}</pre>
     *
     * When the callback is fast and lightweight consider {@linkplain
     * Futures#addCallback(ListenableFuture, FutureCallback) the other overload}
     * or explicit use of {@link MoreExecutors#sameThreadExecutor
     * sameThreadExecutor}. For heavier callbacks, this choice carries some
     * caveats: First, the thread that the callback runs in depends on whether
     * the input {@code Future} is done at the time {@code addCallback} is called
     * and on whether the input {@code Future} is ever cancelled. In particular,
     * {@code addCallback} may execute the callback in the thread that calls
     * {@code addCallback} or {@code Future.cancel}. Second, callbacks may run in
     * an internal thread of the system responsible for the input {@code Future},
     * such as an RPC network thread. Finally, during the execution of a {@code
     * sameThreadExecutor} callback, all other registered but unexecuted
     * listeners are prevented from running, even if those listeners are to run
     * in other executors.
     *
     * <p>For a more general interface to attach a completion listener to a
     * {@code Future}, see {@link ListenableFuture#addListener addListener}.
     *
     * @param future The future attach the callback to.
     * @param callback The callback to invoke when {@code future} is completed.
     * @param executor The executor to run {@code callback} when the future
     *    completes.
     * @since 10.0
     */
    public static <V> void addCallback(final ListenableFuture<V> future,
        final FutureCallback<? super V> callback, Executor executor) {
      Preconditions.checkNotNull(callback);
      Runnable callbackListener = new Runnable() {
        @Override
        public void run() {
          try {
            // TODO(user): (Before Guava release), validate that this
            // is the thing for IE.
            V value = getUninterruptibly(future);
            callback.onSuccess(value);
          } catch (ExecutionException e) {
            callback.onFailure(e.getCause());
          } catch (RuntimeException e) {
            callback.onFailure(e);
          } catch (Error e) {
            callback.onFailure(e);
          }
        }
      };
      future.addListener(callbackListener, executor);
    }

    /**
     * Creates a {@code ListenableFuture} which has its value set immediately upon
     * construction. The getters just return the value. This {@code Future} can't
     * be canceled or timed out and its {@code isDone()} method always returns
     * {@code true}.
     */
    public static <V> ListenableFuture<V> immediateFuture(@Nullable V value) {
      SettableFuture<V> future = SettableFuture.create();
      future.set(value);
      return future;
    }

    /**
     * Returns a {@code ListenableFuture} which has an exception set immediately
     * upon construction.
     *
     * <p>The returned {@code Future} can't be cancelled, and its {@code isDone()}
     * method always returns {@code true}. Calling {@code get()} will immediately
     * throw the provided {@code Throwable} wrapped in an {@code
     * ExecutionException}.
     *
     * @throws Error if the throwable is an {@link Error}.
     */
    public static <V> ListenableFuture<V> immediateFailedFuture(
        Throwable throwable) {
      checkNotNull(throwable);
      SettableFuture<V> future = SettableFuture.create();
      future.setException(throwable);
      return future;
    }

    /**
     * Returns a new {@code ListenableFuture} whose result is the product of
     * applying the given {@code Function} to the result of the given {@code
     * Future}. Example:
     *
     * <pre>   {@code
     *   ListenableFuture<QueryResult> queryFuture = ...;
     *   Function<QueryResult, List<Row>> rowsFunction =
     *       new Function<QueryResult, List<Row>>() {
     *         public List<Row> apply(QueryResult queryResult) {
     *           return queryResult.getRows();
     *         }
     *       };
     *   ListenableFuture<List<Row>> rowsFuture =
     *       transform(queryFuture, rowsFunction);
     * }</pre>
     *
     * <p>Note: This overload of {@code transform} is designed for cases in which
     * the transformation is fast and lightweight, as the method does not accept
     * an {@code Executor} in which to perform the the work. For heavier
     * transformations, this overload carries some caveats: First, the thread
     * that the transformation runs in depends on whether the input {@code
     * Future} is done at the time {@code transform} is called. In particular, if
     * called late, {@code transform} will perform the transformation in the
     * thread that called {@code transform}. Second, transformations may run in
     * an internal thread of the system responsible for the input {@code Future},
     * such as an RPC network thread. Finally, during the execution of a {@code
     * sameThreadExecutor} transformation, all other registered but unexecuted
     * listeners are prevented from running, even if those listeners are to run
     * in other executors.
     *
     * <p>The returned {@code Future} attempts to keep its cancellation state in
     * sync with that of the input future. That is, if the returned {@code Future}
     * is cancelled, it will attempt to cancel the input, and if the input is
     * cancelled, the returned {@code Future} will receive a callback in which it
     * will attempt to cancel itself.
     *
     * <p>An example use of this method is to convert a serializable object
     * returned from an RPC into a POJO.
     *
     * @param future The future to transform
     * @param function A Function to transform the results of the provided future
     *     to the results of the returned future.  This will be run in the thread
     *     that notifies input it is complete.
     * @return A future that holds result of the transformation.
     * @since 9.0 (in 1.0 as {@code compose})
     */
    public static <I, O> ListenableFuture<O> transform(ListenableFuture<I> future,
        final Function<? super I, ? extends O> function) {
      return transform(future, function, MoreExecutors.sameThreadExecutor());
    }

    /**
     * Returns a new {@code ListenableFuture} whose result is the product of
     * applying the given {@code Function} to the result of the given {@code
     * Future}. Example:
     *
     * <pre>   {@code
     *   ListenableFuture<QueryResult> queryFuture = ...;
     *   Function<QueryResult, List<Row>> rowsFunction =
     *       new Function<QueryResult, List<Row>>() {
     *         public List<Row> apply(QueryResult queryResult) {
     *           return queryResult.getRows();
     *         }
     *       };
     *   ListenableFuture<List<Row>> rowsFuture =
     *       transform(queryFuture, rowsFunction, executor);
     * }</pre>
     *
     * <p>The returned {@code Future} attempts to keep its cancellation state in
     * sync with that of the input future. That is, if the returned {@code Future}
     * is cancelled, it will attempt to cancel the input, and if the input is
     * cancelled, the returned {@code Future} will receive a callback in which it
     * will attempt to cancel itself.
     *
     * <p>An example use of this method is to convert a serializable object
     * returned from an RPC into a POJO.
     *
     * <p>Note: For cases in which the transformation is fast and lightweight,
     * consider {@linkplain Futures#transform(ListenableFuture, Function) the
     * other overload} or explicit use of {@link
     * MoreExecutors#sameThreadExecutor}. For heavier transformations, this
     * choice carries some caveats: First, the thread that the transformation
     * runs in depends on whether the input {@code Future} is done at the time
     * {@code transform} is called. In particular, if called late, {@code
     * transform} will perform the transformation in the thread that called
     * {@code transform}.  Second, transformations may run in an internal thread
     * of the system responsible for the input {@code Future}, such as an RPC
     * network thread.  Finally, during the execution of a {@code
     * sameThreadExecutor} transformation, all other registered but unexecuted
     * listeners are prevented from running, even if those listeners are to run
     * in other executors.
     *
     * @param future The future to transform
     * @param function A Function to transform the results of the provided future
     *     to the results of the returned future.
     * @param executor Executor to run the function in.
     * @return A future that holds result of the transformation.
     * @since 9.0 (in 2.0 as {@code compose})
     */
    public static <I, O> ListenableFuture<O> transform(ListenableFuture<I> future,
        final Function<? super I, ? extends O> function, Executor executor) {
      checkNotNull(function);
      Function<I, ListenableFuture<O>> wrapperFunction
          = new Function<I, ListenableFuture<O>>() {
              @Override public ListenableFuture<O> apply(I input) {
                O output = function.apply(input);
                return immediateFuture(output);
              }
          };
      return chain(future, wrapperFunction, executor);
    }

    /**
     * Creates a new {@code ListenableFuture} whose value is a list containing the
     * values of all its input futures, if all succeed. If any input fails, the
     * returned future fails.
     *
     * <p>The list of results is in the same order as the input list.
     *
     * <p>Canceling this future does not cancel any of the component futures;
     * however, if any of the provided futures fails or is canceled, this one is,
     * too.
     *
     * @param futures futures to combine
     * @return a future that provides a list of the results of the component
     *         futures
     * @since 10.0
     */
    @Beta
    public static <V> ListenableFuture<List<V>> allAsList(
        ListenableFuture<? extends V>... futures) {
      return new ListFuture<V>(ImmutableList.copyOf(futures), true,
          MoreExecutors.sameThreadExecutor());
    }

    /**
     * Creates a new {@code ListenableFuture} whose value is a list containing the
     * values of all its input futures, if all succeed. If any input fails, the
     * returned future fails.
     *
     * <p>The list of results is in the same order as the input list.
     *
     * <p>Canceling this future does not cancel any of the component futures;
     * however, if any of the provided futures fails or is canceled, this one is,
     * too.
     *
     * @param futures futures to combine
     * @return a future that provides a list of the results of the component
     *         futures
     * @since 10.0
     */
    @Beta
    public static <V> ListenableFuture<List<V>> allAsList(
        Iterable<? extends ListenableFuture<? extends V>> futures) {
      return new ListFuture<V>(ImmutableList.copyOf(futures), true,
          MoreExecutors.sameThreadExecutor());
    }

    /**
     * <p>Returns a new {@code ListenableFuture} whose result is asynchronously
     * derived from the result of the given {@code Future}. More precisely, the
     * returned {@code Future} takes its result from a {@code Future} produced by
     * applying the given {@code Function} to the result of the original {@code
     * Future}. Example:
     *
     * <pre>   {@code
     *   ListenableFuture<RowKey> rowKeyFuture = indexService.lookUp(query);
     *   Function<RowKey, ListenableFuture<QueryResult>> queryFunction =
     *       new Function<RowKey, ListenableFuture<QueryResult>>() {
     *         public ListenableFuture<QueryResult> apply(RowKey rowKey) {
     *           return dataService.read(rowKey);
     *         }
     *       };
     *   ListenableFuture<QueryResult> queryFuture =
     *       chain(rowKeyFuture, queryFunction, executor);
     * }</pre>
     *
     * <p>The returned {@code Future} attempts to keep its cancellation state in
     * sync with that of the input future and that of the future returned by the
     * chain function. That is, if the returned {@code Future} is cancelled, it
     * will attempt to cancel the other two, and if either of the other two is
     * cancelled, the returned {@code Future} will receive a callback in which it
     * will attempt to cancel itself.
     *
     * <p>Note: For cases in which the work of creating the derived future is
     * fast and lightweight, consider {@linkplain Futures#chain(ListenableFuture,
     * Function) the other overload} or explicit use of {@code
     * sameThreadExecutor}. For heavier derivations, this choice carries some
     * caveats: First, the thread that the derivation runs in depends on whether
     * the input {@code Future} is done at the time {@code chain} is called. In
     * particular, if called late, {@code chain} will run the derivation in the
     * thread that called {@code chain}. Second, derivations may run in an
     * internal thread of the system responsible for the input {@code Future},
     * such as an RPC network thread. Finally, during the execution of a {@code
     * sameThreadExecutor} {@code chain} function, all other registered but
     * unexecuted listeners are prevented from running, even if those listeners
     * are to run in other executors.
     *
     * @param input The future to chain
     * @param function A function to chain the results of the provided future
     *     to the results of the returned future.
     * @param executor Executor to run the function in.
     * @return A future that holds result of the chain.
     * @deprecated Convert your {@code Function} to a {@code AsyncFunction}, and
     *     use {@link #transform(ListenableFuture, AsyncFunction, Executor)}. This
     *     method is scheduled to be removed from Guava in Guava release 12.0.
     */
    @Deprecated
    /*package*/ static <I, O> ListenableFuture<O> chain(ListenableFuture<I> input,
        final Function<? super I, ? extends ListenableFuture<? extends O>>
            function,
        Executor executor) {
      checkNotNull(function);
      ChainingListenableFuture<I, O> chain =
          new ChainingListenableFuture<I, O>(new AsyncFunction<I, O>() {
            @Override
            /*
             * All methods of ListenableFuture are covariant, and we don't expose
             * the object anywhere that would allow it to be downcast.
             */
            @SuppressWarnings("unchecked")
            public ListenableFuture<O> apply(I input) {
              return (ListenableFuture) function.apply(input);
            }
          }, input);
      input.addListener(chain, executor);
      return chain;
    }
}
