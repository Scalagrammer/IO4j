package scg.io4j;

import io.atlassian.fugue.Either;

import static io.atlassian.fugue.Either.left;

public final class TimeoutException extends Throwable {

    public static final Throwable instance = new TimeoutException();

    private static final Either<Throwable, ?> leftProjection = left(instance);

    private TimeoutException() {
        super();
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @SuppressWarnings("unchecked")
    public static  <R> Either<Throwable, R> asLeft() {
        return (Either<Throwable, R>) leftProjection;
    }

    public static  <R> Either<Throwable, R> asLeft(Class<R> right) {
        return asLeft();
    }

}
