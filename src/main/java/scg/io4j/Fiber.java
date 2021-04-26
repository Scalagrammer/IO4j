package scg.io4j;

import io.atlassian.fugue.Unit;

import java.util.concurrent.CompletableFuture;

import static scg.io4j.IO.*;

public interface Fiber<R> {

    IO<R> join();

    IO<Unit> cancel();

    default IO<R> joinOn(Scheduler scheduler) {
        return scheduler.fork(join());
    }

    static <R> Fiber<R> wrap(CompletableFuture<R> promise, IOConnection connected) {
        return new Fiber<>() {
            @Override
            public IO<R> join() {
                return delay(promise::join);
            }

            @Override
            public IO<Unit> cancel() {
                return connected.cancel();
            }
        };
    }

}
