package scg.io4j;

import io.atlassian.fugue.Either;
import lombok.RequiredArgsConstructor;
import scg.io4j.utils.TConsumer;
import scg.io4j.utils.TFunction;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;

public interface Await<R> extends IO<TFunction<Executor, IO<R>>> {
    IO<R> on(Executor executor);
}

@RequiredArgsConstructor
final class AwaitImpl<R> implements Await<R> {

    private final Future<R> blocker;

    @Override
    public IO<R> on(Executor executor) {
        return this.flatMap(f -> f.apply(executor));
    }

    @Override
    public void run(TConsumer<Either<Throwable, TFunction<Executor, IO<R>>>> callback) {
        callback.accept(right(await(blocker)));
    }

    private static <R> TFunction<Executor, IO<R>> await(Future<R> f) {
        return executor -> callback -> {

            Runnable task = () -> {
                try {
                    callback.accept(getAttempt(f));
                } catch (Throwable cause) {
                    callback.accept(left(cause));
                }
            };

            executor.execute(task);

        };
    }

    private static <R> Either<Throwable, R> getAttempt(Future<R> f) {
        try {
            return right(f.get());
        } catch (Throwable cause) {
            return left(cause);
        }
    }

}
