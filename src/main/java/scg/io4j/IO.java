package scg.io4j;

import io.atlassian.fugue.Unit;
import io.atlassian.fugue.Either;
import io.atlassian.fugue.Option;

import lombok.val;
import lombok.Value;
import lombok.RequiredArgsConstructor;

import scg.io4j.utils.*;
import scg.io4j.IO.Async;
import scg.io4j.IO.IOFrame;
import scg.io4j.IO.ContextSwitch;

import java.io.PrintStream;

import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.atlassian.fugue.Unit.VALUE;
import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Eithers.merge;

import static java.util.Map.of;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.StreamSupport.stream;

import static scg.io4j.Fiber.wrap;
import static scg.io4j.Context.empty;
import static scg.io4j.Context.fromMap;

import static scg.io4j.IO.*;
import static scg.io4j.IOConnection.connect;
import static scg.io4j.utils.Callable.always;
import static scg.io4j.utils.TFunction.constantT;
import static scg.io4j.utils.TFunctions.identityT;
import static scg.io4j.Attempt.getAttemptInstance;

public interface IO<R> extends Runnable {

    IO<Unit> U = pure(VALUE);

    @Override
    default void run() {
        this.run(System.err);
    }

    default void run(TConsumer<Either<Throwable, R>> callback) {
        runLoop(this, (null), connect(false), (null), callback, (null));
    }

    default void run(CompletableFuture<R> callback) {
        this.run(attempt -> attempt.fold(callback::completeExceptionally, callback::complete));
    }

    default void run(CountDownLatch latch) {
        this.run(onSuccess(latch));
    }

    default void run(PrintStream err) {
        this.run(printStackTrace(err));
    }

    default IO<R> enrich(Context context) {
        return new ContextBind<>(this, context);
    }

    default IO<R> enrich(Object key, Object value) {
        return this.enrich(of(key, value));
    }

    default IO<R> enrich(java.util.Map<Object, Object> context) {
        return this.enrich(fromMap(context));
    }

    default IO<R> cache() {
        //
        val promise = new CompletableFuture<R>();
        //
        val ready = new AtomicBoolean(false);
        //
        Action runCache = () -> {
            if (ready.compareAndSet(false, true)) run(promise);
        };
        //
        return unit(runCache).as(promise::get);
    }

    default CompletableFuture<R> promise(Executor executor) {
        //
        val promise = new CompletableFuture<R>();
        //
        TConsumer<Either<Throwable, R>> callback = attempt -> {
            attempt.fold(promise::completeExceptionally, promise::complete);
        };
        //
        executor.execute(() -> run(callback));
        //
        return promise;
    }

    default IO<Fiber<R>> start(Scheduler scheduler) {
        return this.start(false, scheduler);
    }

    default IO<Fiber<R>> startInterruptible(Scheduler scheduler) {
        return this.start(true, scheduler);
    }

    default IO<Fiber<R>> start(boolean interruptible, Scheduler scheduler) {
        //
        TBiConsumer<IOConnection, TConsumer<Either<Throwable, Fiber<R>>>> f = (connected, callback) -> {
            //
            val promise = new CompletableFuture<R>();
            //
            TConsumer<Either<Throwable, R>> complete = attempt -> {
                attempt.fold(promise::completeExceptionally, promise::complete);
            };
            //
            runLoop(scheduler.fork(interruptible, this), (null), (connected), (null), (complete), (null));
            //
            callback.accept(right(wrap(promise, connected)));
        };
        //
        return new Async<>(false, f);
    }

    default IO<R> shift(Scheduler scheduler) {
        return this.bind((R value) -> scheduler.fork(pure(value)));
    }

    default IO<Either<Throwable, R>> attempt() {
        return new Bind<>(this, getAttemptInstance());
    }

    default <RR> IO<RR> tailrec(TFunction<R, IO<Either<R, RR>>> f) {
        //
        TFunction<Either<R, RR>, IO<RR>> g = attempt -> {
            return attempt.fold((R value) -> pure(value).tailrec(f), IO::pure);
        };
        //
        return this.bind(f, g);
    }

    default IO<Unit> forever(TFunction<R, IO<R>> loop) {
        return this.tailrec(loop.andThenT(fr -> fr.map(Either::left)));
    }

    default IO<Unit> thenIf(TFunction<R, Boolean> p, IO<Unit> then) {
        return this.flatMap(p.andThenT(cond -> cond ? then : U));
    }

    default IO<Unit> thenIfAsync(TFunction<R, IO<Boolean>> p, IO<Unit> then) {
        return this.bind(p, (cond -> cond ? then : U));
    }

    default <RR> IO<RR> productR(IO<RR> right) {
        return this.bind(constantT(right));
    }

    default <RR> IO<R> productL(IO<RR> right) {
        return right.bind(constantT(this));
    }

    default <RR> IO<Product2<R, RR>> tupleR(RR right) {
        return this.map((R value) -> Product2.apply(value, right));
    }

    default <RR> IO<Product2<RR, R>> tupleL(RR right) {
        return this.map((R value) -> Product2.apply(right, value));
    }

    default <RR> IO<Product2<R, RR>> mproduct(TFunction<R, IO<RR>> f) {
        return this.bind((R value) -> f.applyT(value).map((RR result) -> Product2.apply(value, result)));
    }

    default <RR> IO<Product2<RR, R>> mproductLeft(TFunction<R, IO<RR>> f) {
        return this.bind((R value) -> f.applyT(value).map((RR result) -> Product2.apply(result, value)));
    }

    default <RR> IO<Product2<R, RR>> fproduct(TFunction<R, RR> f) {
        return this.map((R value) -> Product2.apply(value, f.applyT(value)));
    }

    default <RR> IO<Product2<RR, R>> fproductLeft(TFunction<R, RR> f) {
        return this.map((R value) -> Product2.apply(f.applyT(value), value));
    }

    default <RR> IO<RR> map(TFunction<R, RR> f) {
        return new Map<>(this, f);
    }

    default IO<R> chainMap(Iterable<TFunction<R, R>> fs) {
        return suspend(() -> map(stream((fs.spliterator()), false).reduce((identityT()), TFunction::andThenT)));
    }

    default <RR> IO<RR> flatMap(TFunction<R, IO<RR>> f) {
        return this.bind(f);
    }

    default <RR> IO<RR> bind(TFunction<R, IO<RR>> f) {
        return new Bind<>(this, f);
    }

    default <A, B> IO<B> bind(TFunction<R, IO<A>> f1, TFunction<A, IO<B>> f2) {
        return this.bind(f1).bind(f2);
    }

    default <A, B, C> IO<C> bind(TFunction<R, IO<A>> f1, TFunction<A, IO<B>> f2, TFunction<B, IO<C>> f3) {
        return this.bind(f1, f2).bind(f3);
    }

    default <A, B, C, D> IO<D> bind(TFunction<R, IO<A>> f1, TFunction<A, IO<B>> f2, TFunction<B, IO<C>> f3, TFunction<C, IO<D>> f4) {
        return this.bind(f1, f2, f3).bind(f4);
    }

    default <A, B, C, D, E> IO<E> bind(TFunction<R, IO<A>> f1, TFunction<A, IO<B>> f2, TFunction<B, IO<C>> f3, TFunction<C, IO<D>> f4, TFunction<D, IO<E>> f5) {
        return this.bind(f1, f2, f3, f4).bind(f5);
    }

    default <RR> IO<RR> as(RR value) {
        return this.map(constantT(value));
    }

    default <RR> IO<RR> as(Callable<RR> callback) {
        return this.productR(delay(callback));
    }

    default <RR> IO<R> flatTap(TFunction<R, IO<RR>> f) {
        return this.bind((R value) -> f.applyT(value).as(value));
    }

    // constructor

    static <R> IO<R> extractContext(TFunction<Context, R> f) {
        return new SuspendWithContext<>(f.andThenT(Pure::new));
    }

    static <R> IO<Option<R>> findInContext(Object key) {
        return suspend(context -> pure(context.find(key)));
    }

    static <R> IO<R> delay(Callable<R> f) {
        return new Delay<>(f);
    }

    static <R> IO<R> never() {
        return async(callback -> {});
    }

    static IO<Unit> unit(Action action) {
        return suspend(() -> fromEither(action.attempt()));
    }

    static <R> IO<R> suspend(Callable<IO<R>> f) {
        return new Suspend<>(f);
    }

    static <R> IO<R> suspend(TFunction<Context, IO<R>> f) {
        return new SuspendWithContext<>(f);
    }

    static <R> IO<R> pure(R value) {
        return new Pure<>(value);
    }

    static <R> IO<R> raise(Throwable cause) {
        return raise(always(cause));
    }

    static <R> IO<R> raise(Callable<Throwable> cause) {
        return new Raise<>(cause);
    }

    static <R> IO<R> fromEither(Either<Throwable, R> attempt) {
        return attempt.fold(IO::raise, IO::pure);
    }

    static <A> IO<A> async(TConsumer<AsyncCallback<A>> scope) {
        //
        TBiConsumer<IOConnection, TConsumer<Either<Throwable, A>>> f = (connected, callback) -> {
            //
            AtomicBoolean done = new AtomicBoolean(false);
            //
            val idempotent = new AsyncCallback<A>() {
                @Override
                public void success(A value) {
                    if (done.compareAndSet(false, true)) {
                        callback.accept(right(value));
                    }
                }

                @Override
                public void failure(Throwable cause) {
                    if (done.compareAndSet(false, true)) {
                        callback.accept(left(cause));
                    }
                }
            };
            //
            try {
                scope.accept(idempotent);
            } catch (Throwable cause) {
                callback.accept(left(cause));
            }
        };
        //
        return new Async<>(false, f);
    }

    static <R> IO<Unit> loop(IO<R> body) {
        return U.forever(constantT(body.productR(U)));
    }

    static <R> TConsumer<Either<Throwable, R>> printStackTrace(PrintStream err) {
        return attempt -> (attempt.left()).forEach(cause -> cause.printStackTrace(err));
    }

    static <R> TConsumer<Either<Throwable, R>> onSuccess(CountDownLatch successor) {
        return attempt -> {
            if (attempt.isRight()) successor.countDown();
        };
    }

    static <A, B> Tupled2<A, B> tupled(IO<A> fa, IO<B> fb) {
        return new Tupled2<>() {
            @Override
            public <R> IO<R> bind(TFunction2<A, B, IO<R>> f) {
                return fa.bind(a -> fb.bind(b -> f.applyT(a, b)));
            }
        };
    }

    static <A, B, C> Tupled3<A, B, C> tupled(IO<A> fa, IO<B> fb, IO<C> fc) {
        return new Tupled3<>() {
            @Override
            public <R> IO<R> bind(TFunction3<A, B, C, IO<R>> f) {
                return fa.bind(a -> fb.bind(b -> fc.bind(c -> f.applyT(a, b, c))));
            }
        };
    }

    static <A, B, C, D> Tupled4<A, B, C, D> tupled(IO<A> fa, IO<B> fb, IO<C> fc, IO<D> fd) {
        return new Tupled4<>() {
            @Override
            public <R> IO<R> bind(TFunction4<A, B, C, D, IO<R>> f) {
                return fa.bind(a -> fb.bind(b -> fc.bind(c -> fd.bind(d -> f.applyT(a, b, c, d)))));
            }
        };
    }

    static <A, B, C, D, E> Tupled5<A, B, C, D, E> tupled(IO<A> fa, IO<B> fb, IO<C> fc, IO<D> fd, IO<E> fe) {
        return new Tupled5<>() {
            @Override
            public <R> IO<R> bind(TFunction5<A, B, C, D, E, IO<R>> f) {
                return fa.bind(a -> fb.bind(b -> fc.bind(c -> fd.bind(d -> fe.bind(e -> f.applyT(a, b, c, d, e))))));
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static <R> void runLoop( IO<R> source
                           , TFunction bindFirst
                           , IOConnection connected
                           , RestartCallback<R> restart
                           , TConsumer<Either<Throwable, R>> callback
                           , ArrayStack<TFunction<Object, IO<Object>>> bindRest ) {
        /////////////////
        R value = (null);
        ///////////////////////////////
        Context currentContext = empty;
        //////////////////////////////////////////
        RestartCallback restartCallback = restart;
        //////////////
        while (true) {
            //
            if (source instanceof ContextBind) {
                //
                currentContext = ((ContextBind) source).localContext.merge(currentContext);
                //
                source = ((ContextBind) source).source;
                //
            } else if (source instanceof SuspendWithContext) try {
                //
                source = (IO) ((SuspendWithContext) source).f.applyT(currentContext);
                //
            } catch (Throwable cause) {
                //
                source = raise(cause);
                //
            } else if (source instanceof Bind) {
                //
                if (nonNull(bindFirst)) {
                    //
                    if (isNull(bindRest)) bindRest = new ArrayStack<>();
                    //
                    bindRest.push(bindFirst);
                    //
                }
                //
                bindFirst = ((Bind) source).f;
                //
                source = ((Bind) source).source;
                //
            } else if (source instanceof Pure) {
                //
                value = (R) ((Pure) source).value;
                //
                if (isNull(value)) {
                    //
                    throw new NullPointerException("pure(null)");
                    //
                }
                //
                source = (null);
                //
            } else if (source instanceof Delay) try {
                //
                value = (R) ((Delay) source).f.call();
                //
                if (isNull(value)) {
                    //
                    throw new NullPointerException("delay(() -> null)");
                    //
                }
                //
                source = (null);
                //
            } catch (Throwable cause) {
                //
                source = raise(cause);
                //
            } else if (source instanceof Suspend) try {
                //
                source = (IO) ((Suspend) source).f.call();
                //
            } catch (Throwable cause) {
                //
                source = raise(cause);
                //
            } else if (source instanceof Raise) {
                //
                val h = findHandler(bindFirst, bindRest);
                //
                if (isNull(h)) {
                    //
                    callback.accept(left(((Throwable) merge(((Raise) source).cause.attempt()))));
                    //
                    return; // done
                    //
                } else try {
                    //
                    source = (IO<R>) h.recover(((Throwable) merge(((Raise) source).cause.attempt())));
                    //
                } catch (Throwable cause) {
                    //
                    source = raise(cause);
                    //
                } finally {
                    //
                    bindFirst = (null);
                    //
                }
                //
            } else if (source instanceof Async) {
                //
                if (isNull(connected)) connected = connect(true);
                //
                if (isNull(restart)) restart = new RestartCallback<>(callback);
                //
                restart.switchContext(connected).start((Async) source, bindFirst, bindRest);
                //
                return; // done
                //
            } else if (source instanceof ContextSwitch) {
                //
                val modify = ((ContextSwitch) source).modify;
                //
                val next = ((ContextSwitch) source).source;
                //
                val restore = ((ContextSwitch) source).restore;
                //
                val connect = nonNull(connected) ? connected : connect(true);
                //
                connected = (IOConnection) modify.apply(connect);
                //
                source = next;
                //
                if (connected != connect) {
                    //
                    if (nonNull(restartCallback)) {
                        //
                        restartCallback.switchContext(connected);
                        //
                    }
                    //
                    if (nonNull(restore)) {
                        //
                        source = new Bind(next, new RestoreContext(connect, restore));
                        //
                    }
                    //
                }
                //
            } else if (source instanceof Map) {
                //
                if (nonNull(bindFirst)) {
                    //
                    if (isNull(bindRest)) bindRest = new ArrayStack<>();
                    //
                    bindRest.push(bindFirst);
                    //
                }
                //
                bindFirst = (Map) source;
                //
                source = ((Map) source).source;
                //
            } else {
                //
                throw new RuntimeException("Unknown IO constructor");
                //
            }
            //
            if (nonNull(value)) {
                //
                var bind = popNextBind(bindFirst, bindRest);
                //
                if (nonNull(bind)) try {
                    //
                    source = (IO<R>) bind.apply(value);
                    //
                } catch (Throwable cause) {
                    //
                    source = raise(cause);
                    //
                } finally {
                    //
                    value = (null);
                    //
                    bindFirst = (null);
                    //
                } else {
                    //
                    callback.accept(right(value));
                    //
                    return; // done
                }
            }
            //
        }
    }

    static TFunction<Object, IO<Object>> popNextBind( TFunction<Object, IO<Object>> bindFirst
                                                    , ArrayStack<TFunction<Object, IO<Object>>> bindRest ) {

        if (nonNull(bindFirst) && !(bindFirst instanceof ErrorHandler)) return bindFirst;

        if (isNull(bindRest)) return (null);

        while (true) {

            bindFirst = bindRest.pop();

            if (isNull(bindFirst) || !(bindFirst instanceof ErrorHandler)) {
                return bindFirst;
            }
        }
    }

    static IOFrame<Object, IO<Object>> findHandler( TFunction<Object, IO<Object>> bindFirst
                                                  , ArrayStack<TFunction<Object, IO<Object>>> bindRest ) {

        if (bindFirst instanceof IOFrame) {
            return (IOFrame<Object, IO<Object>>) bindFirst;
        } else if (isNull(bindRest)) {
            return (null);
        } else while (true) {

            bindFirst = bindRest.pop();

            if (isNull(bindFirst) || (bindFirst instanceof IOFrame)) {
                return (IOFrame<Object, IO<Object>>) bindFirst;
            }
        }
    }

    abstract class IOFrame<A, R> implements TFunction<A, R> {

        abstract R recover(Throwable e);

        final R fold(Either<Throwable, A> e) {
            return e.fold(this::recover, this);
        }

    }

    @Value
    class Bind<A, B> implements IO<B> {
        IO<A> source;
        TFunction<A, IO<B>> f;
    }

    @Value
    class Map<A, B> implements IO<B>, TFunction<A, IO<B>> {

        IO<A> source;
        TFunction<A, B> f;

        @Override
        public IO<B> applyT(A arg) throws Throwable {
            return f.andThenT(Pure::new).applyT(arg);
        }

    }

    @Value
    class Suspend<A> implements IO<A> {
        Callable<IO<A>> f;
    }

    @Value
    class SuspendWithContext<A> implements IO<A> {
        TFunction<Context, IO<A>> f;
    }

    @Value
    class Raise<A> implements IO<A> {
        Callable<Throwable> cause;
    }

    @Value
    class Delay<A> implements IO<A> {
        Callable<A> f;
    }

    @Value
    class Pure<A> implements IO<A> {
        A value;
    }

    @Value
    class Async<A> implements IO<A> {
        boolean trampolineAfter;
        TBiConsumer<IOConnection, TConsumer<Either<Throwable, A>>> callback;
    }

    @Value
    class ContextBind<A> implements IO<A> {

        IO<A> source;
        Context localContext;

        @Override
        public IO<A> enrich(Context context) {
            return new ContextBind<>(source, localContext.merge(context));
        }

    }

    @Value
    class ContextSwitch<A> implements IO<A> {
        IO<A> source;
        TFunction<IOConnection, IOConnection> modify;
        TFunction4<A, Throwable, IOConnection, IOConnection, IOConnection> restore;
    }

}

@RequiredArgsConstructor
final class RestoreContext<R> extends IOFrame<R, IO<R>> {

    private final IOConnection connected;
    private final TFunction4<R, Throwable, IOConnection, IOConnection, IOConnection> restore;

    @Override
    public IO<R> applyT(R result) {
        return new ContextSwitch<>(pure(result), current -> restore.apply(result, (null), connected, current), (null));
    }

    @Override
    public IO<R> recover(Throwable cause) {
        return new ContextSwitch<>(raise(cause), current -> restore.apply((null), cause, connected, current), (null));
    }

}

@SuppressWarnings({"rawtypes", "unchecked"})
final class Attempt<A> extends IOFrame<A, IO<Either<Throwable, A>>> {

    private static final Attempt instance = new Attempt();

    @Override
    public IO<Either<Throwable, A>> applyT(A value) {
        return pure(right(value));
    }

    @Override
    public IO<Either<Throwable, A>> recover(Throwable cause) {
        return pure(left(cause));
    }

    static <R> Attempt<R> getAttemptInstance() {
        return instance;
    }

}

@RequiredArgsConstructor
final class RestartCallback<A> implements TConsumer<Either<Throwable, A>>, Runnable {

    private final TConsumer<Either<Throwable, A>> callback;

    private boolean callable = false;

    private IOConnection connection = null;

    private boolean trampolineAfter = false;

    private Either<Throwable, A> value = null;

    private TFunction<Object, IO<Object>> bindFirst = null;

    private ArrayStack<TFunction<Object, IO<Object>>> bindRest = null;

    RestartCallback<A> switchContext(IOConnection connection) {
        this.connection = connection;
        return this;
    }

    void start( Async<A> task
              , TFunction<Object, IO<Object>> bindFirst
              , ArrayStack<TFunction<Object, IO<Object>>> bindRest ) {
        //
        this.callable = true;
        //
        this.bindRest  =  bindRest;
        this.bindFirst = bindFirst;
        //
        this.trampolineAfter = task.isTrampolineAfter();
        //
        (task.getCallback()).accept(connection, this);
    }

    @Override
    public void run() {
        // N.B. this has to be set to null *before* the signal
        // otherwise a race condition can happen ;-)
        var localValue = value;
        //
        this.value = (null);
        //
        this.signal(localValue);
    }

    @Override
    public void acceptT(Either<Throwable, A> attempt) {
        if (callable) {
            //
            this.callable = false;
            //
            if (trampolineAfter) {
                //
                this.value = attempt;
                //
                this.run();
                //
            } else this.signal(attempt);
        }
    }

    private void signal(Either<Throwable, A> attempt) {
        // Auto-cancelable logic: in case the connection was cancelled,
        // we interrupt the bind continuation
        if (!connection.isCanceled()) {
            runLoop(fromEither(attempt), (bindFirst), (connection), this, (callback), (bindRest));
        }
    }

}
