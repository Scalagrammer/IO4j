package scg.io4j;

import io.atlassian.fugue.Unit;
import lombok.RequiredArgsConstructor;
import scg.io4j.utils.NamingThreadFactory;
import scg.io4j.utils.TConsumer;
import scg.io4j.utils.TSupplier;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static io.atlassian.fugue.Unit.VALUE;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static scg.io4j.IO.async;
import static scg.io4j.IO.unit;
import static scg.io4j.utils.TSupplier.ofInstanceT;

public interface Scheduler extends Executor {

    IO<Unit> shutdown();

    IO<Unit> fork();

    IO<Unit> schedule(long delay, TimeUnit unit);

    <R> IO<R> timeout(long delay, TimeUnit unit, TSupplier<Throwable> cause);

    default  <R> IO<R> timeout(long delay, TimeUnit unit, Throwable cause) {
        return this.timeout(delay, unit, ofInstanceT(cause));
    }

    default <R> IO<R> timeout(long delay, TimeUnit unit) {
        return this.timeout(delay, unit, TimeoutException.instance);
    }

    static Scheduler fixed(String name, int corePoolSize) {
        return wrap(newScheduledThreadPool(corePoolSize, factory(name, false)));
    }

    static Scheduler fixedDaemon(String name, int corePoolSize) {
        return wrap(newScheduledThreadPool(corePoolSize, factory(name, true)));
    }

    static Scheduler single(String name) {
        return wrap(newSingleThreadScheduledExecutor(factory(name, false)));
    }

    static Scheduler two(String name) {
        return fixed(name, 2);
    }

    static Scheduler singleDaemon(String name) {
        return wrap(newSingleThreadScheduledExecutor(factory(name, true)));
    }

    static ThreadFactory factory(String namePattern, boolean daemon) {
        return new NamingThreadFactory(namePattern, daemon);
    }

    private static Scheduler wrap(ScheduledExecutorService executor) {
        return new SchedulerImpl(executor);
    }

}

@RequiredArgsConstructor
final class SchedulerImpl implements Scheduler {

    private final ScheduledExecutorService executor;

    @Override
    public IO<Unit> schedule(long delay, TimeUnit unit) {
        ////////////////////////////////////////////////////
        TConsumer<AsyncCallback<Unit>> scope = callback -> {
            this.executor.schedule(() -> callback.success(VALUE), delay, unit);
        };
        ////////////////////
        return async(scope);
        //
    }

    @Override
    public <R> IO<R> timeout(long delay, TimeUnit unit, TSupplier<Throwable> supply) {
        /////////////////////////////////////////////////
        TConsumer<AsyncCallback<R>> scope = callback -> {
            //////////////////////////
            Runnable command = () -> {
                try {
                    callback.failure(supply.getT());
                } catch (Throwable cause) {
                    callback.failure(cause);
                }
            };
            /////////////////////////////////////////////
            this.executor.schedule(command, delay, unit);
            //
        };
        ////////////////////
        return async(scope);
        //
    }

    @Override
    public IO<Unit> fork() {
        ////////////////////////////////////////////////////
        TConsumer<AsyncCallback<Unit>> scope = callback -> {
            this.executor.execute(() -> callback.success(VALUE));
        };
        ////////////////////
        return async(scope);
    }

    @Override
    public IO<Unit> shutdown() {
        return unit(executor::shutdownNow);
    }

    @Override
    public void execute(Runnable command) {
        this.executor.execute(command);
    }

}
