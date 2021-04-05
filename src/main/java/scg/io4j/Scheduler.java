package scg.io4j;

import io.atlassian.fugue.Unit;
import scg.io4j.utils.NamingThreadFactory;

import java.util.concurrent.*;

import static io.atlassian.fugue.Either.right;
import static java.util.concurrent.Executors.*;
import static io.atlassian.fugue.Unit.VALUE;
import static scg.io4j.IO.unit;

public interface Scheduler extends Executor, AutoCloseable {

    IO<Unit> schedule(long delay, TimeUnit unit);

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

    private static Scheduler wrap(ScheduledExecutorService executor) {
        return new Scheduler() {

            @Override
            public void close() {
                executor.shutdown();
            }

            @Override
            public IO<Unit> schedule(long delay, TimeUnit unit) {
                return callback -> executor.schedule(() -> callback.accept(right(VALUE)), delay, unit);
            }

            @Override
            public void execute(Runnable command) {
                executor.execute(command);
            }
        };
    }

    private static ThreadFactory factory(String namePattern, boolean daemon) {
        return new NamingThreadFactory(namePattern, daemon);
    }

}
