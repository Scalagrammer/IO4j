package scg.io4j;

import io.atlassian.fugue.Unit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static scg.io4j.IO.*;

public interface Fiber<R> {

    IO<R> join();

    IO<Unit> cancel(boolean mayInterruptIfRunning);

    default IO<R> joinOn(Executor executor) {
        return fork(executor).then(join());
    }

    default IO<Unit> cancel() {
        return this.cancel(false);
    }

    static IO<Unit> interrupt(Fiber<?> f) {
        return f.cancel(true);
    }

    static <R> Fiber<R> wrap(CompletableFuture<R> f) {
        return new Fiber<>() {
            @Override
            public IO<R> join() {
                return delay(f::join);
            }

            @Override
            public IO<Unit> cancel(boolean mayInterruptIfRunning) {
                return unit(() -> f.cancel(mayInterruptIfRunning));
            }
        };
    }

}
