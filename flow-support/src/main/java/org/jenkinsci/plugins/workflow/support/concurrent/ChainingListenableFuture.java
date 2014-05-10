package org.jenkinsci.plugins.workflow.support.concurrent;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import javax.annotation.Nullable;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Uninterruptibles.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An implementation of {@code ListenableFuture} that also implements
 * {@code Runnable} so that it can be used to nest ListenableFutures.
 * Once the passed-in {@code ListenableFuture} is complete, it calls the
 * passed-in {@code Function} to generate the result.
 *
 * <p>If the function throws any checked exceptions, they should be wrapped
 * in a {@code UndeclaredThrowableException} so that this class can get
 * access to the cause.
 */
class ChainingListenableFuture<I, O>
    extends AbstractFuture<O> implements Runnable {

  private AsyncFunction<? super I, ? extends O> function;
  private ListenableFuture<? extends I> inputFuture;
  private volatile ListenableFuture<? extends O> outputFuture;
  private final BlockingQueue<Boolean> mayInterruptIfRunningChannel =
      new LinkedBlockingQueue<Boolean>(1);
  private final CountDownLatch outputCreated = new CountDownLatch(1);

  ChainingListenableFuture(
      AsyncFunction<? super I, ? extends O> function,
      ListenableFuture<? extends I> inputFuture) {
    this.function = checkNotNull(function);
    this.inputFuture = checkNotNull(inputFuture);
  }

  /**
   * Delegate the get() to the input and output futures, in case
   * their implementations defer starting computation until their
   * own get() is invoked.
   */
  @Override
  public O get() throws InterruptedException, ExecutionException {
    if (!isDone()) {
      // Invoking get on the inputFuture will ensure our own run()
      // method below is invoked as a listener when inputFuture sets
      // its value.  Therefore when get() returns we should then see
      // the outputFuture be created.
      ListenableFuture<? extends I> inputFuture = this.inputFuture;
      if (inputFuture != null) {
        inputFuture.get();
      }

      // If our listener was scheduled to run on an executor we may
      // need to wait for our listener to finish running before the
      // outputFuture has been constructed by the function.
      outputCreated.await();

      // Like above with the inputFuture, we have a listener on
      // the outputFuture that will set our own value when its
      // value is set.  Invoking get will ensure the output can
      // complete and invoke our listener, so that we can later
      // get the result.
      ListenableFuture<? extends O> outputFuture = this.outputFuture;
      if (outputFuture != null) {
        outputFuture.get();
      }
    }
    return super.get();
  }

  /**
   * Delegate the get() to the input and output futures, in case
   * their implementations defer starting computation until their
   * own get() is invoked.
   */
  @Override
  public O get(long timeout, TimeUnit unit) throws TimeoutException,
      ExecutionException, InterruptedException {
    if (!isDone()) {
      // Use a single time unit so we can decrease remaining timeout
      // as we wait for various phases to complete.
      if (unit != NANOSECONDS) {
        timeout = NANOSECONDS.convert(timeout, unit);
        unit = NANOSECONDS;
      }

      // Invoking get on the inputFuture will ensure our own run()
      // method below is invoked as a listener when inputFuture sets
      // its value.  Therefore when get() returns we should then see
      // the outputFuture be created.
      ListenableFuture<? extends I> inputFuture = this.inputFuture;
      if (inputFuture != null) {
        long start = System.nanoTime();
        inputFuture.get(timeout, unit);
        timeout -= Math.max(0, System.nanoTime() - start);
      }

      // If our listener was scheduled to run on an executor we may
      // need to wait for our listener to finish running before the
      // outputFuture has been constructed by the function.
      long start = System.nanoTime();
      if (!outputCreated.await(timeout, unit)) {
        throw new TimeoutException();
      }
      timeout -= Math.max(0, System.nanoTime() - start);

      // Like above with the inputFuture, we have a listener on
      // the outputFuture that will set our own value when its
      // value is set.  Invoking get will ensure the output can
      // complete and invoke our listener, so that we can later
      // get the result.
      ListenableFuture<? extends O> outputFuture = this.outputFuture;
      if (outputFuture != null) {
        outputFuture.get(timeout, unit);
      }
    }
    return super.get(timeout, unit);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    /*
     * Our additional cancellation work needs to occur even if
     * !mayInterruptIfRunning, so we can't move it into interruptTask().
     */
    if (super.cancel(mayInterruptIfRunning)) {
      // This should never block since only one thread is allowed to cancel
      // this Future.
      putUninterruptibly(mayInterruptIfRunningChannel, mayInterruptIfRunning);
      cancel(inputFuture, mayInterruptIfRunning);
      cancel(outputFuture, mayInterruptIfRunning);
      return true;
    }
    return false;
  }

  private void cancel(@Nullable Future<?> future,
      boolean mayInterruptIfRunning) {
    if (future != null) {
      future.cancel(mayInterruptIfRunning);
    }
  }

  @Override
  public void run() {
    try {
      I sourceResult;
      try {
        sourceResult = getUninterruptibly(inputFuture);
      } catch (CancellationException e) {
        // Cancel this future and return.
        // At this point, inputFuture is cancelled and outputFuture doesn't
        // exist, so the value of mayInterruptIfRunning is irrelevant.
        cancel(false);
        return;
      } catch (ExecutionException e) {
        // Set the cause of the exception as this future's exception
        setException(e.getCause());
        return;
      }

      final ListenableFuture<? extends O> outputFuture = this.outputFuture =
          function.apply(sourceResult);
      if (isCancelled()) {
        // Handles the case where cancel was called while the function was
        // being applied.
        // There is a gap in cancel(boolean) between calling sync.cancel()
        // and storing the value of mayInterruptIfRunning, so this thread
        // needs to block, waiting for that value.
        outputFuture.cancel(
            takeUninterruptibly(mayInterruptIfRunningChannel));
        this.outputFuture = null;
        return;
      }
      outputFuture.addListener(new Runnable() {
          @Override
          public void run() {
            try {
              // Here it would have been nice to have had an
              // UninterruptibleListenableFuture, but we don't want to start a
              // combinatorial explosion of interfaces, so we have to make do.
              set(getUninterruptibly(outputFuture));
            } catch (CancellationException e) {
              // Cancel this future and return.
              // At this point, inputFuture and outputFuture are done, so the
              // value of mayInterruptIfRunning is irrelevant.
              cancel(false);
              return;
            } catch (ExecutionException e) {
              // Set the cause of the exception as this future's exception
              setException(e.getCause());
            } finally {
              // Don't pin inputs beyond completion
              ChainingListenableFuture.this.outputFuture = null;
            }
          }
        }, MoreExecutors.sameThreadExecutor());
    } catch (UndeclaredThrowableException e) {
      // Set the cause of the exception as this future's exception
      setException(e.getCause());
    } catch (Exception e) {
      // This exception is irrelevant in this thread, but useful for the
      // client
      setException(e);
    } catch (Error e) {
      // Propagate errors up ASAP - our superclass will rethrow the error
      setException(e);
    } finally {
      // Don't pin inputs beyond completion
      function = null;
      inputFuture = null;
      // Allow our get routines to examine outputFuture now.
      outputCreated.countDown();
    }
  }
}
