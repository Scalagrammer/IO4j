package scg.io4j;

import io.atlassian.fugue.*;
import scg.io4j.utils.Action;
import scg.io4j.utils.Callable;
import scg.io4j.utils.AsyncCallback;

import java.io.PrintStream;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.atlassian.fugue.Option.*;
import static java.util.stream.Stream.*;
import static io.atlassian.fugue.Pair.pair;
import static io.atlassian.fugue.Unit.VALUE;
import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Functions.constant;
import static io.atlassian.fugue.Suppliers.ofInstance;

import static scg.io4j.Fiber.wrap;
import static scg.io4j.ProgressStatus.*;
import static scg.io4j.utils.AsyncCallback.wrap;
import static scg.io4j.utils.Callable.always;

@FunctionalInterface
public interface IO<R> extends Runnable {

    void run(Consumer<Either<Throwable, R>> callback);

    @Override
    default void run() {
        this.run(System.out);
    }

    default <T> T to(Function<IO<R>, T> f) {
        return f.apply(this);
    }

    default void run(Handler h) {
        this.run(handle(h));
    }

    default void run(PrintStream out) {
        this.run(printStackTrace(out));
    }

    default void run(ProgressCheck bar) {

        bar.update(RUNNING);

        this.run(handleStatus(bar::update));

    }

    default void run(CountDownLatch successor) {
        this.run(countDown(successor));
    }

    default CompletableFuture<R> toFuture(Executor executor) {

        CompletableFuture<R> promise = new CompletableFuture<>();

        Consumer<Either<Throwable, R>> callback = attempt -> {
            if (attempt.isRight()) {
                (attempt.right()).forEach(promise::complete);
            } else {
                (attempt.left()).forEach(promise::completeExceptionally);
            }
        };

        this.shift(executor).run(callback); // start async

        return promise;

    }

    default IO<Either<Throwable, R>> attempt() {
        return callback -> run(attempt -> callback.accept(right(attempt)));
    }

    default IO<R> shift(Executor executor) {
        return callback -> executor.execute(() -> run(callback));
    }

    default <RR> IO<RR> then(IO<RR> next) {
        return this.flatMap(constant(next));
    }

    default <RR> IO<R> tap(IO<RR> next) {
        return this.flatTap(constant(next));
    }

    default IO<R> filter(Predicate<R> p) {
        return this.flatMap(r -> p.test(r) ? raise(NoSuchElementException::new) : pure(r));
    }

    default IO<R> recoverWith(Class<? extends Throwable> causeType, IO<R> alternative) {
        return callback -> {

            Consumer<Either<Throwable, R>> catcher = attempt -> {

                if (attempt.isRight()) {
                    callback.accept(attempt);
                    return;
                }

                Consumer<Throwable> match = cause -> {
                    if (causeType.isAssignableFrom(cause.getClass())) {
                        alternative.run(callback);
                    } else {
                        callback.accept(attempt);
                    }
                };

                (attempt.swap()).forEach(match);

            };

            this.run(catcher);

        };
    }

    default IO<R> filterNot(Predicate<R> p) {
        return this.filter(p.negate());
    }

    default <RR> IO<RR> fold(Supplier<RR> zero, BiFunction<RR, R, RR> f) {
        return this.map(r -> f.apply(zero.get(), r));
    }

    default IO<R> foldMonoid(Monoid<R> monoid) {
        return this.map(r -> monoid.append(monoid.zero(), r));
    }

    default IO<Unit> thenIf(Predicate<R> p, IO<Unit> proceed) {
        return this.flatMap(r -> p.test(r) ? proceed : unit());
    }

    default IO<Fiber<R>> fiber(Executor executor) {
        return delay(() -> wrap(toFuture(executor)));
    }

    default <RR> IO<Pair<R, RR>> productR(IO<RR> right) {
        return this.flatMap(r -> right.map(rr -> pair(r, rr)));
    }

    default <RR> IO<Pair<RR, R>> productL(IO<RR> right) {
        return this.flatMap(r -> right.map(rr -> pair(rr, r)));
    }

    default <RR> IO<Pair<R, RR>> mproduct(Function<R, IO<RR>> f) {
        return this.flatMap(r -> f.apply(r).map(rr -> pair(r, rr)));
    }

    default <RR> IO<Pair<R, RR>> fproduct(Function<R, RR> f) {
        return this.map(r -> pair(r, f.apply(r)));
    }

    default <RR> IO<RR> map(Function<R, RR> f) {
        return callback -> run(attempt -> callback.accept(attempt.map(f)));
    }

    default <RR> IO<RR> flatMap(Function<R, IO<RR>> f) {
        return callback -> {

            Consumer<Either<Throwable, R>> g = attempt -> {

                Either<Throwable, IO<RR>> mapped = attempt.map(f);

                if (mapped.isRight()) {
                    (mapped.right()).forEach(thunk -> thunk.run(callback));
                } else {
                    (mapped.left()).forEach(cause -> callback.accept(left(cause)));
                }
            };

            this.run(g);

        };
    }

    default <A, B> IO<B> bind(Function<R, IO<A>> f1, Function<A, IO<B>> f2) {
        return this.flatMap(f1).flatMap(f2);
    }

    default <A, B, C> IO<C> bind(Function<R, IO<A>> f1, Function<A, IO<B>> f2, Function<B, IO<C>> f3) {
        return this.flatMap(f1).flatMap(f2).flatMap(f3);
    }

    default <A, B, C, D> IO<D> bind(Function<R, IO<A>> f1, Function<A, IO<B>> f2, Function<B, IO<C>> f3, Function<C, IO<D>> f4) {
        return this.flatMap(f1).flatMap(f2).flatMap(f3).flatMap(f4);
    }

    default <A, B, C, D, E> IO<E> bind(Function<R, IO<A>> f1, Function<A, IO<B>> f2, Function<B, IO<C>> f3, Function<C, IO<D>> f4, Function<D, IO<E>> f5) {
        return this.flatMap(f1).flatMap(f2).flatMap(f3).flatMap(f4).flatMap(f5);
    }

    default <RR> IO<RR> as(RR value) {
        return this.as(always(value));
    }

    default <RR> IO<RR> as(Callable<RR> callback) {
        return this.then(delay(callback));
    }

    default <RR> IO<R> flatTap(Function<R, IO<RR>> f) {
        return this.flatMap(arg -> f.apply(arg).as(arg));
    }

    static <A, R> Function<A, IO<R>> by(Function<A, R> f) {
        return f.andThen(IO::pure);
    }

    static <A, R> Function<IO<A>, IO<R>> lift(Function<A, R> f) {
        return arg -> arg.map(f);
    }

    static <R> IO<R> delay(Callable<R> delayed) {
        return callback -> callback.accept(delayed.attempt());
    }

    static IO<Unit> fork(Executor executor) {
        return pure(VALUE).shift(executor);
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

    static <R> IO<Function<Executor, IO<R>>> await(Future<R> blocker) {
        return pure(executor -> fork(executor).as(blocker::get));
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
        return raise(ofInstance(cause));
    }

    static <R> IO<R> raise(Supplier<Throwable> cause) {
        return callback -> callback.accept(left(cause.get()));
    }

    static <R> IO<Function<Executor, IO<Either<R, R>>>> race(IO<R> left, IO<R> right) {

        Function<Executor, IO<Either<R, R>>> f = executor -> callback -> {

            AtomicBoolean done = new AtomicBoolean(false);

            Consumer<Either<Throwable, R>> fl = attempt -> {
                if (done.compareAndSet(false, true)) {
                    if (attempt.isLeft()) {
                        (attempt.left()).forEach(cause -> callback.accept(left(cause)));
                    } else {
                        (attempt.right()).forEach(r -> callback.accept(right(left(r))));
                    }
                }
            };

            Consumer<Either<Throwable, R>> fr = attempt -> {
                if (done.compareAndSet(false, true)) {
                    if (attempt.isLeft()) {
                        (attempt.left()).forEach(cause -> callback.accept(left(cause)));
                    } else {
                        (attempt.right()).forEach(r -> callback.accept(right(right(r))));
                    }
                }
            };

            left.shift(executor).run(fl);

            right.shift(executor).run(fr);

        };

        return pure(f);

    }

    static <R> IO<R> async(Consumer<AsyncCallback<R>> scope) {
        return callback -> {
            try {
                scope.accept(wrap(callback));
            } catch (Throwable cause) {
                callback.accept(left(cause));
            }
        };
    }

    static IO<Function<Scheduler, IO<Unit>>> sleep(long delay, TimeUnit unit) {
        return pure(s -> s.schedule(delay, unit));
    }

    static <R> IO<Function<Scheduler, IO<R>>> timeout(long delay, TimeUnit unit) {
        return sleep(delay, unit).then(raise(TimeoutException::new));
    }

    @SafeVarargs
    static <R> IO<Stream<R>> sequence(IO<R>... ops) {
        return sequence(of(ops));
    }

    static <R> IO<Stream<R>> sequence(Stream<IO<R>> ops) {

        Callable<IO<Stream<R>>> f = () -> {

            BinaryOperator<IO<Stream<R>>> concat = (a, b) -> {
                return a.flatMap(as -> b.map(bs -> concat(as, bs)));
            };

            return ops.map(e -> e.map(Stream::of)).reduce(pure(empty()), concat);

        };

        return suspend(f);

    }

    static <R> Function<Boolean, IO<R>> ifM(IO<R> ifTrue, IO<R> ifFalse) {
        return condition -> condition ? ifTrue : ifFalse;
    }

    static <A> Monoid<IO<A>> monoidK(Monoid<A> monoid) {
        return new Monoid<>() {
            @Override
            public IO<A> zero() {
                return pure(monoid.zero());
            }

            @Override
            public IO<A> append(IO<A> fa, IO<A> fb) {
                return fa.flatMap(a -> fb.map(b -> monoid.append(a, b)));
            }
        };
    }

    @SafeVarargs
    static <R> IO<Function<Monoid<R>, IO<R>>> foldMonoid(IO<R>... ops) {
        return foldMonoid(of(ops));
    }

    static <R> IO<Function<Monoid<R>, IO<R>>> foldMonoid(Stream<IO<R>> ops) {
        return pure(monoid -> sequence(ops).map(s -> s.reduce(monoid.zero(), monoid::append)));
    }

    @SafeVarargs
    static <R> IO<Function<BinaryOperator<R>, IO<Option<R>>>> reduce(IO<R>... ops) {
        return reduce(of(ops));
    }

    static <R> IO<Function<BinaryOperator<R>, IO<Option<R>>>> reduce(Stream<IO<R>> ops) {
        return pure(accumulator -> sequence(ops).map(s -> fromOptional(s.reduce(accumulator))));
    }

    @SafeVarargs
    static <R> IO<BiFunction<R, BinaryOperator<R>, IO<R>>> foldLeft(IO<R>... ops) {
        return foldLeft(of(ops));
    }

    static <R> IO<BiFunction<R, BinaryOperator<R>, IO<R>>> foldLeft(Stream<IO<R>> ops) {
        return pure((zero, accumulator) -> sequence(ops).map(s -> s.reduce(zero, accumulator)));
    }

    static <A, B, R> Function<BiFunction<A, B, R>, R> apply(A a, B b) {
        return f -> f.apply(a, b);
    }

    static <A, R> Function<Function<A, R>, R> apply(A a) {
        return f -> f.apply(a);
    }

    private static <R> Consumer<Either<Throwable, R>> printStackTrace(PrintStream s) {
        return attempt -> (attempt.left()).forEach(cause -> cause.printStackTrace(s));
    }

    private static <R> Consumer<Either<Throwable, R>> handle(Handler h) {
        return attempt -> (attempt.left()).forEach(h::put);
    }

    private static <R> Consumer<Either<Throwable, R>> countDown(CountDownLatch onFinish) {
        return attempt -> {
            if (attempt.isRight()) {
                onFinish.countDown();
            }
        };
    }

    private static <R> Consumer<Either<Throwable, R>> handleStatus(Consumer<ProgressStatus> onStatus) {
        return attempt -> {
            if (attempt.isRight()) {
                onStatus.accept(SUCCESS);
            } else {
                onStatus.accept(FAILURE);
            }
        };
    }

}
