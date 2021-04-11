package scg.io4j;

import io.atlassian.fugue.Either;
import io.atlassian.fugue.Unit;
import lombok.RequiredArgsConstructor;
import scg.io4j.utils.NamingThreadFactory;
import scg.io4j.utils.TSupplier;

import java.util.concurrent.*;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static java.util.concurrent.Executors.*;
import static io.atlassian.fugue.Unit.VALUE;
import static scg.io4j.IO.unit;

public interface Scheduler extends Executor, AutoCloseable {

    IO<Unit> schedule(long delay, TimeUnit unit);

    <R> IO<R> timeout(long delay, TimeUnit unit, Throwable cause);

    <R> IO<R> timeout(long delay, TimeUnit unit, TSupplier<Throwable> cause);

    default <R> IO<R> timeout(long delay, TimeUnit unit) {
        return this.timeout(delay, unit, TimeoutException.instance);
    }

    default IO<Unit> shutdown() {
        return unit(this::close);
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

    private static final Either<Throwable, Unit> rightUnit = right(VALUE);

    private final ScheduledExecutorService executor;

    @Override
    public IO<Unit> schedule(long delay, TimeUnit unit) {
        return callback -> executor.schedule(() -> callback.accept(rightUnit), delay, unit);
    }

    @Override
    public <R> IO<R> timeout(long delay, TimeUnit unit, Throwable cause) {
        return callback -> executor.schedule(() -> callback.accept(left(cause)), delay, unit);
    }

    @Override
    public <R> IO<R> timeout(long delay, TimeUnit unit, TSupplier<Throwable> cause) {
        return callback -> {

            Runnable command = () -> {
                try {
                    callback.accept(left(cause.get()));
                } catch (Throwable cause2) {
                    callback.accept(left(cause2));
                }
            };

            this.executor.schedule(command, delay, unit);

        };
    }

    @Override
    public void close() {
        this.executor.shutdown();
    }

    @Override
    public void execute(Runnable command) {
        this.executor.execute(command);
    }

}
