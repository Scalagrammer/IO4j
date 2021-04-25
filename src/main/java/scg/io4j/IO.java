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
import java.util.function.Predicate;
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

import static scg.io4j.Context.empty;
import static scg.io4j.Context.fromMap;
import static scg.io4j.Fiber.wrap;
import static scg.io4j.IO.*;
import static scg.io4j.OptionT.optionT;
import static scg.io4j.IOConnection.connect;
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

    default void run(CountDownLatch latch) {
        this.run(IO.onSuccess(latch));
    }

    default void run(PrintStream errors) {
        this.run(IO.printStackTrace(errors));
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

    default CompletableFuture<R> promise(Executor executor) {
        ///////////////////////////////////
        val f = new CompletableFuture<R>();
        ///////////////////////////////////////////////////////
        TConsumer<Either<Throwable, R>> callback = attempt -> {
            if (attempt.isRight()) {
                (attempt.right()).forEach(f::complete);
            } else {
                (attempt.left()).forEach(f::completeExceptionally);
            }
        };
        //////////////////////////////////////
        executor.execute(() -> run(callback));
        /////////
        return f;
    }

    default IO<Fiber<R>> start(Scheduler scheduler) {

        TConsumer<AsyncCallback<Fiber<R>>> scope = callback -> {

            val connect = connect(true);

            val promise = new CompletableFuture<R>();

            TConsumer<Either<Throwable, R>> fold = e -> {
                e.fold(promise::completeExceptionally, promise::complete);
            };

            runLoop((scheduler.fork()).productR(this), (null), (connect), (null), (fold), (null));

            callback.success(wrap(promise, connect));

        };

        return async(scope);

    }

    default IO<Either<Throwable, R>> attempt() {
        return new Bind<>(this, getAttemptInstance());
    }

    default <RR> IO<RR> tailrec(TFunction<R, IO<Either<R, RR>>> f) {
        ///////////////////////////////////////////
        TFunction<Either<R, RR>, IO<RR>> g = e -> {
            return e.fold((R r) -> pure(r).tailrec(f), IO::pure);
        };
        ///////////////////////
        return this.bind(f, g);
    }

    default IO<Unit> forever(TFunction<R, IO<R>> loop) {
        return this.tailrec(loop.andThenT(fr -> fr.map(Either::left)));
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

    default <RR> IO<Product2<R, RR>> mproduct(TFunction<R, IO<RR>> f) {
        return this.flatMap(r -> f.applyT(r).map(rr -> Product2.apply(r, rr)));
    }

    default <RR> IO<Product2<RR, R>> mproductLeft(TFunction<R, IO<RR>> f) {
        return this.flatMap(r -> f.applyT(r).map(rr -> Product2.apply(rr, r)));
    }

    default <RR> IO<Product2<R, RR>> fproduct(TFunction<R, RR> f) {
        return this.map(r -> Product2.apply(r, f.applyT(r)));
    }

    default <RR> IO<Product2<RR, R>> fproductLeft(TFunction<R, RR> f) {
        return this.map(r -> Product2.apply(f.applyT(r), r));
    }

    default <RR> IO<RR> map(TFunction<R, RR> f) {
        return new Map<>(this, f);
    }

    default IO<R> chainMap(Iterable<TFunction<R, R>> fs) {
        return suspend(() -> map(stream(fs.spliterator(), false).reduce(identityT(), TFunction::andThenT)));
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
        return this.flatMap(arg -> f.apply(arg).as(arg));
    }

    // constructors

    static <L, R> IO<Option<Product2<L, R>>> tupled(IO<Option<L>> lf, IO<Option<R>> rf) {
        return optionT(lf).flatMap(l -> optionT(rf).map(r -> Product2.apply(l, r))).toIO();
    }

    static <R> IO<R> extractContext(TFunction<Context, R> f) {
        return new SuspendWithContext<>(f.andThenT(Pure::new));
    }

    static <R> IO<Option<R>> findInContext(Object key, Class<R> tag) {
        return findInContext(key);
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
        return raise(() -> cause);
    }

    static <R> IO<R> raise(Callable<Throwable> cause) {
        return new Raise<>(cause);
    }

    static <R> IO<R> fromEither(Either<Throwable, R> e) {
        return e.fold(IO::raise, IO::pure);
    }

    static <A> IO<A> async(TConsumer<AsyncCallback<A>> scope) {

        TBiConsumer<IOConnection, TConsumer<Either<Throwable, A>>> f = (connected, callback) -> {

            AtomicBoolean done = new AtomicBoolean(false);

            val idempotent = new AsyncCallback<A>() {
                @Override
                public void success(A result) {
                    if (done.compareAndSet(false, true)) {
                        callback.accept(right(result));
                    }
                }

                @Override
                public void failure(Throwable cause) {
                    if (done.compareAndSet(false, true)) {
                        callback.accept(left(cause));
                    }
                }
            };

            try {
                scope.accept(idempotent);
            } catch (Throwable cause) {
                callback.accept(left(cause));
            }

        };

        return new Async<>(false, f);

    }

    static <R> IO<Unit> loop(IO<R> body) {
        return U.forever(constantT(body.productR(U)));
    }

    private static <R> TConsumer<Either<Throwable, R>> printStackTrace(PrintStream errors) {
        return attempt -> (attempt.left()).forEach(cause -> cause.printStackTrace(errors));
    }

    private static <R> TConsumer<Either<Throwable, R>> onSuccess(CountDownLatch successor) {
        return attempt -> {
            if (attempt.isRight()) successor.countDown();
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static <R> void runLoop( IO<R> source
                           , TFunction bindFirst
                           , IOConnection connected
                           , RestartCallback<R> restart
                           , TConsumer<Either<Throwable, R>> callback
                           , ArrayStack<TFunction<Object, IO<Object>>> bindRest ) {
        //
        Context currentContext = empty;
        //
        R value = (null);
        //
        RestartCallback restartCallback = restart;
        //
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
                source = (null);
                //
            } else if (source instanceof Delay) try {
                //
                value = (R) ((Delay) source).f.call();
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
                    return;
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
            } else if (source instanceof Async) {
                //
                if (isNull(connected)) connected = connect(true);
                //
                if (isNull(restart)) restart = new RestartCallback<>(callback).switchContext(connected);
                //
                restart.start((Async) source, bindFirst, bindRest);
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
                throw new RuntimeException("Unknown IO constructor");
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
                    value     = (null);
                    //
                    bindFirst = (null);
                    //
                } else {
                    //
                    callback.accept(right(value));
                    //
                    return; // done
                    //
                }
            }
        }
    }

    static TFunction<Object, IO<Object>> popNextBind( TFunction<Object, IO<Object>> bindFirst
                                                    , ArrayStack<TFunction<Object, IO<Object>>> bindRest ) {

        if (nonNull(bindFirst) && !(bindFirst instanceof ErrorHandler)) return bindFirst;

        if (isNull(bindRest)) return (null);

        var bindNext = bindRest.pop();

        while (true) if (isNull(bindNext)) {
            return (null);
        } else if (!(bindNext instanceof ErrorHandler)) {
            return (bindNext);
        }
    }

    static IOFrame<Object, IO<Object>> findHandler( TFunction<Object, IO<Object>> bind
                                                  , ArrayStack<TFunction<Object, IO<Object>>> binds ) {

        if (bind instanceof IOFrame) {
            return (IOFrame<Object, IO<Object>>) bind;
        } else if (isNull(binds)) {
            return (null);
        } else while (true) {

            val pop = binds.pop();

            if (isNull(pop)) {
                return (null);
            } else if (pop instanceof IOFrame) {
                return (IOFrame<Object, IO<Object>>) pop;
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
final class RestartCallback<A> implements TConsumer<Either<Throwable, A>>, Runnable {

    private final TConsumer<Either<Throwable, A>> callback;

    private IOConnection connection;

    private boolean callable        = false;
    private boolean trampolineAfter = false;

    private TFunction<Object, IO<Object>> bindFirst = null;

    private ArrayStack<TFunction<Object, IO<Object>>> bindRest = null;

    private Either<Throwable, A> value = null;

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
        this.bindRest  = bindRest;
        this.bindFirst = bindFirst;
        //
        this.trampolineAfter = task.isTrampolineAfter();
        //
        (task.getCallback()).accept(connection, this);
    }

    private void signal(Either<Throwable, A> e) {
        // Auto-cancelable logic: in case the connection was cancelled,
        // we interrupt the bind continuation
        if (!connection.isCanceled()) {
            runLoop(fromEither(e), bindFirst, connection, this, callback, bindRest);
        }
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
    public void acceptT(Either<Throwable, A> e) {
        if (callable) {
            //
            this.callable = false;
            //
            if (trampolineAfter) {
                //
                this.value = e;
                //
                this.run();
                //
            } else {
                this.signal(e);
            }
        }
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
