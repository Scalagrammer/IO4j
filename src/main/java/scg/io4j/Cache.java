package scg.io4j;

import io.atlassian.fugue.Either;

import lombok.RequiredArgsConstructor;

import scg.io4j.utils.TConsumer;

import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static io.atlassian.fugue.Eithers.cond;

import static java.util.Objects.nonNull;

@RequiredArgsConstructor
final class Cache<R> implements IO<R> {

    private final AtomicReference<CompletableFuture<R>> promise = new AtomicReference<>();

    private final IO<R> source;

    @Override
    public void run(TConsumer<Either<Throwable, R>> callback) {
        this.promise.updateAndGet(compute(source)).whenComplete(accept(callback));
    }

    private static <R> UnaryOperator<CompletableFuture<R>> compute(IO<R> source) {
        return promise -> nonNull(promise) ? promise : source.promise(Runnable::run);
    }

    private static <R> BiConsumer<R, Throwable> accept(TConsumer<Either<Throwable, R>> callback) {
        return (result, cause) -> callback.accept(cond(nonNull(result), cause, result));
    }

}
