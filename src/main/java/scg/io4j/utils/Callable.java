package scg.io4j.utils;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;

import io.atlassian.fugue.Either;

@FunctionalInterface
public interface Callable<R> extends AttemptCapable<R> {

    R call() throws Throwable;

    @Override
    default Either<Throwable, R> attempt() {
        try {
            return right(call());
        } catch (Throwable cause) {
            return left(cause);
        }
    }

    static <R> Callable<R> always(R value) {
        return () -> value;
    }

}
