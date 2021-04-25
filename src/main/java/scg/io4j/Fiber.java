package scg.io4j;

import io.atlassian.fugue.Unit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static scg.io4j.IO.*;

public interface Fiber<R> {

    IO<R> join();

//    default IO<R> joinOn(Executor executor) {
//        return fork(executor).productR(join());
//    }

    IO<Unit> cancel();

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
