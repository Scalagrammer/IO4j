package scg.io4j;

import io.atlassian.fugue.*;
import lombok.val;
import scg.io4j.utils.*;
import scg.io4j.utils.Callable;

import java.io.PrintStream;

import java.util.function.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static java.util.stream.Stream.*;

import static io.atlassian.fugue.Unit.VALUE;
import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Suppliers.ofInstance;

import static scg.io4j.Fiber.wrap;
import static scg.io4j.OptionT.optionT;
import static scg.io4j.utils.AsyncCallback.wrap;
import static scg.io4j.utils.TFunction.constantT;
import static scg.io4j.utils.TSupplier.ofInstanceT;

@FunctionalInterface
public interface IO<R> extends Runnable {

    void run(TConsumer<Either<Throwable, R>> callback);

    @Override
    default void run() {
        this.run(System.out);
    }

    default void run(PrintStream out) {
        this.run(printStackTrace(out));
    }

    default void run(CountDownLatch successor) {
        this.run(countDown(successor));
    }

    default <RR> RR to(TFunction<IO<R>, RR> f) {
        return f.apply(this);
    }

    default CompletableFuture<R> toFuture(Executor executor) {

        CompletableFuture<R> promise = new CompletableFuture<>();

        TConsumer<Either<Throwable, R>> callback = attempt -> {
            if (attempt.isRight()) {
                (attempt.right()).forEach(promise::complete);
            } else {
                (attempt.left()).forEach(promise::completeExceptionally);
            }
        };

        executor.execute(() -> run(callback));

        return promise;
    }

    default IO<String> show(Show<R> f) {
        return this.map(f);
    }

    default IO<R> shift() {
        return this.shift(commonPool());
    }

    default IO<R> shift(Executor executor) {
        return callback -> executor.execute(() -> run(callback));
    }

    default IO<Fiber<R>> fiber() {
        return this.fiber(commonPool());
    }

    default IO<Fiber<R>> fiber(Executor executor) {
        return delay(() -> wrap(toFuture(executor)));
    }

    default IO<Either<Throwable, R>> attempt() {
        return callback -> run(attempt -> callback.accept(right(attempt)));
    }

    @SuppressWarnings("StatementWithEmptyBody")
    default <RR> IO<RR> tailRecM(TFunction<R, IO<Either<R, RR>>> loop) {

        class IOStack {

            private IO<Either<R, RR>> top = flatMap(loop);

            void push(IO<Either<R, RR>> e) { this.top = e; }

            boolean popAndRun(TConsumer<Either<Throwable, Either<R, RR>>> callback) {

                val local = this.top; // get current

                this.top = (null); // "reset" state ("pop")

                val defined = nonNull(local);

                if (defined) local.run(callback); // run callback if exists

                return defined;

            }
        }

        TConsumer<AsyncCallback<RR>> scope = callback -> {

            IOStack stack = new IOStack();

            TConsumer<Either<R, RR>> onSuccess = result -> {
                if (result.isRight()) {
                    (result.right()).forEach(callback::success);
                } else {
                    (result.swap()).map(loop).forEach(stack::push);
                }
            };

            TConsumer<Either<Throwable, Either<R, RR>>> iteration = attempt -> {
                if (attempt.isRight()) {
                    (attempt.right()).forEach(onSuccess);
                } else {
                    (attempt.left()).forEach(callback::failure);
                }
            };

            while (stack.popAndRun(iteration));

        };

        return IO.async(scope);

    }

    default IO<R> matchError(Class<? extends Throwable> causeType, Supplier<R> result) {
        return callback -> {

            TConsumer<Either<Throwable, R>> catcher = attempt -> {

                if (attempt.isRight()) {
                    callback.accept(attempt);
                    return;
                }

                Consumer<Throwable> match = cause -> {
                    if (causeType.isInstance(cause)) {
                        callback.accept(right(result.get()));
                    } else {
                        callback.accept(attempt);
                    }
                };

                (attempt.swap()).forEach(match);

            };

            this.run(catcher);
        };
    }

    default IO<R> matchErrorWith(Class<? extends Throwable> causeType, IO<R> result) {
        return callback -> {

            TConsumer<Either<Throwable, R>> catcher = attempt -> {

                if (attempt.isRight()) {
                    callback.accept(attempt);
                    return;
                }

                Consumer<Throwable> match = cause -> {
                    if (causeType.isInstance(cause)) {
                        result.run(callback);
                    } else {
                        callback.accept(attempt);
                    }
                };

                (attempt.swap()).forEach(match);

            };

            this.run(catcher);
        };
    }

    default IO<R> matchError(Class<? extends Throwable> causeType, R result) {
        return this.matchError(causeType, ofInstance(result));
    }

    default IO<R> recover(TFunction<Throwable, R> f) {
        return callback -> {

            Consumer<Throwable> onFailure = cause -> {
                callback.accept(right(f.apply(cause)));
            };

            TConsumer<Either<Throwable, R>> catcher = attempt -> {
                if (attempt.isRight()) {
                    callback.accept(attempt);
                } else {
                    (attempt.left()).forEach(onFailure);
                }
            };

            this.run(catcher);
        };
    }

    default IO<R> recoverWith(Function<Throwable, IO<R>> f) {
        return callback -> {

            Consumer<Throwable> onFailure = cause -> {
                f.apply(cause).run(callback);
            };

            TConsumer<Either<Throwable, R>> catcher = attempt -> {
                if (attempt.isRight()) {
                    callback.accept(attempt);
                } else {
                    (attempt.left()).forEach(onFailure);
                }
            };

            this.run(catcher);
        };
    }

    default IO<Unit> thenIf(Predicate<R> p, IO<Unit> then) {
        return this.flatMap(r -> p.test(r) ? then : unit());
    }

    default IO<Unit> thenIfAsync(TFunction<R, IO<Boolean>> p, IO<Unit> then) {
        return this.bind(p, (cond -> cond ? then : unit()));
    }

    default <RR> IO<RR> productR(IO<RR> right) {
        return this.flatMap(constantT(right));
    }

    default <RR> IO<R> productL(IO<RR> right) {
        return right.flatMap(constantT(this));
    }

    default <RR> IO<Product2<R, RR>> tupleR(RR right) {
        return this.map(r -> Product2.apply(r, right));
    }

    default <RR> IO<Product2<RR, R>> tupleL(RR right) {
        return this.map(r -> Product2.apply(right, r));
    }

    default <RR> IO<Product2<R, RR>> mproduct(Function<R, IO<RR>> f) {
        return this.flatMap(r -> f.apply(r).map(rr -> Product2.apply(r, rr)));
    }

    default <RR> IO<Product2<RR, R>> mproductLeft(Function<R, IO<RR>> f) {
        return this.flatMap(r -> f.apply(r).map(rr -> Product2.apply(rr, r)));
    }

    default <RR> IO<Product2<R, RR>> fproduct(Function<R, RR> f) {
        return this.map(r -> Product2.apply(r, f.apply(r)));
    }

    default <RR> IO<Product2<RR, R>> fproductLeft(TFunction<R, RR> f) {
        return this.map(r -> Product2.apply(f.apply(r), r));
    }

    default <RR> IO<RR> map(TFunction<R, RR> f) {
        return callback -> run(attempt -> callback.accept(attempt.map(f)));
    }

    default <RR> IO<RR> cast(Class<RR> type) {
        return this.map(type::cast);
    }

    default <RR> IO<RR> flatMap(TFunction<R, IO<RR>> f) {
        return callback -> {

            TConsumer<Either<Throwable, R>> g = attempt -> {
                if (attempt.isRight()) {

                    val e = attempt.map(f);

                    if (e.isRight()) {
                        (e.right()).forEach(thunk -> thunk.run(callback));
                    } else {
                        (e.left()).forEach(cause -> callback.accept(left(cause)));
                    }
                } else { // fail-fast
                    (attempt.left()).forEach(cause -> callback.accept(left(cause)));
                }
            };

            this.run(g);

        };
    }

    default <A, B> IO<B> bind(TFunction<R, IO<A>> f1, TFunction<A, IO<B>> f2) {
        return this.flatMap(f1).flatMap(f2);
    }

    default <A, B, C> IO<C> bind(TFunction<R, IO<A>> f1, TFunction<A, IO<B>> f2, TFunction<B, IO<C>> f3) {
        return this.flatMap(f1).flatMap(f2).flatMap(f3);
    }

    default <A, B, C, D> IO<D> bind(TFunction<R, IO<A>> f1, TFunction<A, IO<B>> f2, TFunction<B, IO<C>> f3, TFunction<C, IO<D>> f4) {
        return this.flatMap(f1).flatMap(f2).flatMap(f3).flatMap(f4);
    }

    default <A, B, C, D, E> IO<E> bind(TFunction<R, IO<A>> f1, TFunction<A, IO<B>> f2, TFunction<B, IO<C>> f3, TFunction<C, IO<D>> f4, TFunction<D, IO<E>> f5) {
        return this.flatMap(f1).flatMap(f2).flatMap(f3).flatMap(f4).flatMap(f5);
    }

    default <RR> IO<RR> as(RR value) {
        return this.map(constantT(value));
    }

    default <RR> IO<RR> as(Callable<RR> callback) {
        return this.productR(delay(callback));
    }

    default <RR> IO<R> flatTap(TFunction<R, IO<RR>> f) {
        return this.flatMap(arg -> f.apply(arg).as(arg));
    }

    // constructors

    static <L, R> IO<Option<Product2<L, R>>> tupled(IO<Option<L>> lf, IO<Option<R>> rf) {
        return optionT(lf).flatMap(l -> optionT(rf).map(r -> Product2.apply(l, r))).toIO();
    }

    @SafeVarargs
    static <R> Sequence<R> sequence(IO<R>... ops) {
        return sequence(of(ops));
    }

    static <R> Sequence<R> sequence(Stream<IO<R>> value) {
        return new SequenceImpl<>(value);
    }

    static <R> IO<TFunction<Boolean, IO<R>>> ifM(IO<R> ifTrue, IO<R> ifFalse) {
        return pure(condition -> condition ? ifTrue : ifFalse);
    }

    static IO<Unit> fork() {
        return fork(commonPool());
    }

    static IO<Unit> fork(Executor executor) {
        return callback -> executor.execute(() -> callback.accept(right(VALUE)));
    }

    static <R> Race<R> race(IO<R> lr, IO<R> rr) {
        return new RaceImpl<>(lr, rr);
    }

    static <A, R> TFunction<A, IO<R>> by(TFunction<A, R> f) {
        return f.andThenT(IO::pure);
    }

    static <A, R> TFunction<IO<A>, IO<R>> lift(TFunction<A, R> f) {
        return arg -> arg.map(f);
    }

    static <R> IO<R> delay(Callable<R> delayed) {
        return callback -> callback.accept(delayed.attempt());
    }

    static <R> IO<R> never() {
        return callback -> {};
    }

    static IO<Unit> unit() {
        return pure(VALUE);
    }

    static IO<Unit> unit(Action action) {
        return callback -> callback.accept(action.attempt());
    }

    static <R> Await<R> await(Future<R> blocker) {
        return new AwaitImpl<>(blocker);
    }

    static <R> IO<R> suspend(Callable<IO<R>> f) {
        return callback -> {
            try {
                (f.call()).run(callback);
            } catch (Throwable cause) {
                callback.accept(left(cause));
            }
        };
    }

    static <R> IO<R> pure(R value) {
        return callback -> callback.accept(right(value));
    }

    static <R> IO<R> raise(Throwable cause) {
        return raise(ofInstanceT(cause));
    }

    static <R> IO<R> fromEither(Either<Throwable, R> e) {
        return callback -> callback.accept(e);
    }

    static <R> IO<R> raise(TSupplier<Throwable> cause) {
        return callback -> {
            try {
                callback.accept(left(cause.get()));
            } catch (Throwable cause2) {
                callback.accept(left(cause2));
            }
        };
    }

    static <R> IO<R> async(TConsumer<AsyncCallback<R>> scope) {
        return callback -> {
            try {
                scope.accept(wrap(callback));
            } catch (Throwable cause) {
                callback.accept(left(cause));
            }
        };
    }

    static Task<Unit> sleep(long delay, TimeUnit unit) {
        return new SleepTask(delay, unit);
    }

    static <R> Task<R> timeout(long delay, TimeUnit unit) {
        return new TimeoutTask<>(delay, unit);
    }

    static <A> Extract<A> extract(Extractable<A> e) {
        return callback -> callback.accept(right(e));
    }

    static <A> Extract<A> extract(IO<A> fa) {
        return fa.map(a -> ((Extractable<A>) Product.apply(a)))::run;
    }

    static <A, B> Extract2<A, B> extract(Extractable2<A, B> e) {
        return callback -> callback.accept(right(e));
    }

    static <A, B> Extract2<A, B> extract(IO<A> fa, IO<B> fb) {
        return fa.flatMap(a -> fb.map(b -> ((Extractable2<A, B>) Product2.apply(a, b))))::run;
    }

    static <A, B, C> Extract3<A, B, C> extract(Extractable3<A, B, C> e) {
        return callback -> callback.accept(right(e));
    }

    static <A, B, C> Extract3<A, B, C> extract(IO<A> fa, IO<B> fb, IO<C> fc) {
        return fa.flatMap(a -> fb.flatMap(b -> fc.map(c -> ((Extractable3<A, B, C>) Product3.apply(a, b, c)))))::run;
    }

    static <A, B, C, D> Extract4<A, B, C, D> extract(IO<A> fa, IO<B> fb, IO<C> fc, IO<D> fd) {
        return fa.flatMap(a -> fb.flatMap(b -> fc.flatMap(c -> fd.map(d -> ((Extractable4<A, B, C, D>) Product4.apply(a, b, c, d))))))::run;
    }

    static <A, B, C, D> Extract4<A, B, C, D> extract(Extractable4<A, B, C, D> e) {
        return callback -> callback.accept(right(e));
    }

    static <A, B, C, D, E> Extract5<A, B, C, D, E> extract(Extractable5<A, B, C, D, E> e) {
        return callback -> callback.accept(right(e));
    }

    static <A, B, C, D, E> Extract5<A, B, C, D, E> extract(IO<A> fa, IO<B> fb, IO<C> fc, IO<D> fd, IO<E> fe) {
        return fa.flatMap(a -> fb.flatMap(b -> fc.flatMap(c -> fd.flatMap(d -> fe.map(e -> ((Extractable5<A, B, C, D, E>) Product5.apply(a, b, c, d, e)))))))::run;
    }

    private static <R> TConsumer<Either<Throwable, R>> printStackTrace(PrintStream s) {
        return attempt -> (attempt.left()).forEach(cause -> cause.printStackTrace(s));
    }

    private static <R> TConsumer<Either<Throwable, R>> countDown(CountDownLatch onFinish) {
        return attempt -> {
            if (attempt.isRight()) {
                onFinish.countDown();
            }
        };
    }

}
