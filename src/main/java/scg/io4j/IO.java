package scg.io4j;

import io.atlassian.fugue.Either;
import io.atlassian.fugue.Option;
import io.atlassian.fugue.Unit;
import lombok.val;
import scg.io4j.utils.Callable;
import scg.io4j.utils.*;

import java.io.PrintStream;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Suppliers.ofInstance;
import static io.atlassian.fugue.Unit.VALUE;
import static java.lang.Thread.interrupted;
import static java.util.Objects.nonNull;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static java.util.stream.Stream.generate;
import static java.util.stream.Stream.of;
import static scg.io4j.Fiber.wrap;
import static scg.io4j.OptionT.optionT;
import static scg.io4j.utils.AsyncCallback.wrap;
import static scg.io4j.utils.TFunction.constantT;

@FunctionalInterface
public interface IO<R> extends Runnable {

    IO<Unit> U = pure(VALUE);

    void run(TConsumer<Either<Throwable, R>> callback);

    @Override
    default void run() {
        this.run(System.err);
    }

    default void run(CountDownLatch latch) {
        this.run(onSuccess(latch));
    }

    default void run(PrintStream errors) {
        this.run(printStackTrace(errors));
    }

    default IO<R> cache() {
        return new Cache<>(this);
    }

    default CompletableFuture<R> promise(Executor executor) {

        CompletableFuture<R> f = new CompletableFuture<>();

        TConsumer<Either<Throwable, R>> callback = attempt -> {
            if (attempt.isRight()) {
                (attempt.right()).forEach(f::complete);
            } else {
                (attempt.left()).forEach(f::completeExceptionally);
            }
        };

        executor.execute(() -> run(callback));

        return f;

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
        return delay(() -> wrap(promise(executor)));
    }

    default IO<Either<Throwable, R>> attempt() {
        return callback -> run(attempt -> callback.accept(right(attempt)));
    }

    @SuppressWarnings("StatementWithEmptyBody")
    default <RR> IO<RR> tailrec(TFunction<R, IO<Either<R, RR>>> f) {

        class IOStack {

            private IO<Either<R, RR>> top = flatMap(f);

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
                    (result.swap()).map(f).forEach(stack::push);
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

    default <RR> IO<RR> forever(TFunction<R, IO<R>> it) {
        return this.tailrec(it.andThenT(fr -> fr.map(Either::left)));
    }

    default IO<R> matchError(Class<? extends Throwable> causeType, Supplier<R> result) {
        return callback -> {

            TConsumer<Either<Throwable, R>> catcher = attempt -> {

                if (attempt.isRight()) {
                    callback.accept(attempt);
                    return;
                }

                for (val cause : attempt.left()) {
                    if (causeType.isInstance(cause)) {
                        callback.accept(right(result.get()));
                    } else {
                        callback.accept(attempt);
                    }
                }

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

                for (val cause : attempt.left()) {
                    if (causeType.isInstance(cause)) {
                        result.run(callback);
                    } else {
                        callback.accept(attempt);
                    }
                }

            };

            this.run(catcher);

        };
    }

    default IO<R> matchError(Class<? extends Throwable> causeType, R result) {
        return this.matchError(causeType, ofInstance(result));
    }

    default IO<R> recover(TFunction<Throwable, R> f) {
        return callback -> {

            TConsumer<Either<Throwable, R>> catcher = attempt -> {

                if (attempt.isRight()) {
                    callback.accept(attempt);
                    return;
                }

                for (val cause : attempt.left()) {
                    callback.accept(right(f.apply(cause)));
                }

            };

            this.run(catcher);

        };
    }

    default IO<R> recoverWith(Function<Throwable, IO<R>> f) {
        return callback -> {

            TConsumer<Either<Throwable, R>> catcher = attempt -> {

                if (attempt.isRight()) {
                    callback.accept(attempt);
                    return;
                }

                for (val cause : attempt.left()) {
                    f.apply(cause).run(callback);
                }

            };

            this.run(catcher);

        };
    }

    default IO<Unit> thenIf(Predicate<R> p, IO<Unit> then) {
        return this.flatMap(r -> p.test(r) ? then : U);
    }

    default IO<Unit> thenIfAsync(TFunction<R, IO<Boolean>> p, IO<Unit> then) {
        return this.bind(p, (cond -> cond ? then : U));
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

    default Seq<R> replicate(long n) {
        return seq(generate(() -> this).limit(n));
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
    static <R> Seq<R> seq(IO<R>... ops) {
        return seq(of(ops));
    }

    static <R> Seq<R> seq(Stream<IO<R>> value) {
        return new SeqImpl<>(value);
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
        return fromEither(right(value));
    }

    static <R> IO<R> raise(Throwable cause) {
        return fromEither(left(cause));
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

    static <R> IO<R> fromEither(Either<Throwable, R> e) {
        return callback -> callback.accept(e);
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

    static <R> IO<R> loop(IO<R> fr) {
        return callback -> {
            while (!interrupted()) fr.run(callback);
        };
    }

    private static <R> TConsumer<Either<Throwable, R>> printStackTrace(PrintStream errors) {
        return attempt -> (attempt.left()).forEach(cause -> cause.printStackTrace(errors));
    }

    private static <R> TConsumer<Either<Throwable, R>> onSuccess(CountDownLatch successor) {
        return attempt -> {
            if (attempt.isRight()) successor.countDown();
        };
    }

}
