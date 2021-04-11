package scg.io4j;

import io.atlassian.fugue.Either;
import io.atlassian.fugue.Eithers;
import lombok.RequiredArgsConstructor;
import scg.io4j.utils.AsyncCallback;
import scg.io4j.utils.TConsumer;
import scg.io4j.utils.TFunction;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static java.util.concurrent.ForkJoinPool.commonPool;

@FunctionalInterface
public interface Race<R> extends IO<TFunction<Executor, IO<Either<R, R>>>> {

    default IO<R> merge() {
        return this.merge(commonPool());
    }

    default IO<R> merge(Executor executor) {
        return this.start(executor).map(Eithers::merge);
    }

    default <RR> IO<RR> mergeMap(TFunction<R, RR> f) {
        return this.start(commonPool()).map(f.composeT(Eithers::merge));
    }

    default <RR> IO<RR> mergeFlatMap(TFunction<R, IO<RR>> f) {
        return this.start(commonPool()).flatMap(f.composeT(Eithers::merge));
    }

    default IO<Either<R, R>> start() {
        return this.start(commonPool());
    }

    default IO<Either<R, R>> start(Executor executor) {
        return this.flatMap(f -> f.apply(executor));
    }

}

@RequiredArgsConstructor
final class RaceImpl<R> implements Race<R>, TFunction<Executor, IO<Either<R, R>>> {

    private final IO<R> lr;
    private final IO<R> rr;

    @Override
    public void run(TConsumer<Either<Throwable, TFunction<Executor, IO<Either<R, R>>>>> callback) {
        callback.accept(right(this));
    }

    @Override
    public IO<Either<R, R>> applyT(Executor executor) {

        AtomicBoolean done = new AtomicBoolean(false);

        TConsumer<AsyncCallback<Either<R, R>>> scope = callback -> {

            TConsumer<Either<Throwable, R>> fl = attempt -> {
                if (done.compareAndSet(false, true)) {
                    if (attempt.isLeft()) {
                        (attempt.left()).forEach(callback::failure);
                    } else {
                        (attempt.right()).forEach(r -> callback.success(left(r)));
                    }
                }
            };

            TConsumer<Either<Throwable, R>> fr = attempt -> {
                if (done.compareAndSet(false, true)) {
                    if (attempt.isLeft()) {
                        (attempt.left()).forEach(callback::failure);
                    } else {
                        (attempt.right()).forEach(r -> callback.success(right(r)));
                    }
                }
            };

            executor.execute(() -> lr.run(fl));
            executor.execute(() -> rr.run(fr));

        };

        return IO.async(scope);

    }
}