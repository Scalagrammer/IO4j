package scg.io4j;

import io.atlassian.fugue.Unit;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.val;
import scg.io4j.IO.IOFrame;
import scg.io4j.utils.Action;
import scg.io4j.utils.Callable;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;
import static lombok.AccessLevel.PACKAGE;
import static scg.io4j.FList.nil;
import static scg.io4j.IO.*;
import static scg.io4j.IOPlatform.composeErrors;

@NoArgsConstructor(access = PACKAGE)
public abstract class IOConnection {

    abstract public IO<Unit> cancel();

    abstract public IO<Unit> pop();

    abstract public boolean isCanceled();

    abstract public void push(IO<Unit> token);

    abstract public void pushPair(IOConnection lc, IOConnection rc);

    abstract public boolean tryReactivate();

    public void push(Action tokenAction) {
        this.push(unit(tokenAction));
    }

    public static IOConnection connect(boolean cancelable) {
        return cancelable ? (new Cancelable()) : Uncancelable.instance;
    }

    @SafeVarargs
    private static IO<Unit> cancelAll(IO<Unit>... cancelable) {
        if (cancelable.length == 0) {
            return U;
        } else {
            return IO.suspend(() -> cancelAll(asList(cancelable).iterator()));
        }
    }

    private static IO<Unit> cancelAll(FList<IO<Unit>> cancelable) {
        if (cancelable.isEmpty()) {
            return U;
        } else {
            return IO.suspend(() -> cancelAll(cancelable.iterator()));
        }
    }

    private static IO<Unit> cancelAll(Iterator<IO<Unit>> cursor) {
        if (!cursor.hasNext()) {
            return U;
        } else {
            return IO.suspend(() -> new CancelAllFrame(cursor).loop());
        }
    }

    private static final class Uncancelable extends IOConnection {

        static final IOConnection instance = new Uncancelable();

        @Override
        public IO<Unit> cancel() {
            return U;
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void push(IO<Unit> token) {}

        @Override
        public void pushPair(IOConnection lh, IOConnection rh) {}

        @Override
        public IO<Unit> pop() {
            return U;
        }

        @Override
        public boolean tryReactivate() {
            return true;
        }

    }

    private static final class Cancelable extends IOConnection {

        private final AtomicReference<FList<IO<Unit>>> state = new AtomicReference<>(nil());

        @Override
        public IO<Unit> cancel() {

            Callable<IO<Unit>> f = () -> {

                val s = state.getAndSet(nil());

                return s.nonEmpty() ? cancelAll(s) : U;

            };

            return IO.suspend(f);

        }

        @Override
        public boolean isCanceled() {
            return isNull(state.get());
        }

        @Override
        public void push(IO<Unit> cancelable) {
            while (true) {

                val s = state.get();

                if (state.compareAndSet(s, s.prepend(cancelable))) break ;

            }
        }

        @Override
        public void pushPair(IOConnection a, IOConnection b) {
            this.push(cancelAll(a.cancel(), b.cancel()));
        }

        @Override
        public IO<Unit> pop() {
            while (true) {

                val s = state.get();

                if (s.isEmpty()) {
                    return U;
                } else if (state.compareAndSet(s, s.getTail())) {
                    return s.getHead();
                }
            }
        }

        @Override
        public boolean tryReactivate() {
            return this.state.compareAndSet(nil(), nil());
        }

    }

    @RequiredArgsConstructor
    private static final class CancelAllFrame extends IOFrame<Unit, IO<Unit>> {

        private final Iterator<IO<Unit>> cursor;

        private volatile FList<Throwable> errors = nil();

        @Override
        public IO<Unit> applyT(Unit unit) {
            return this.loop();
        }

        IO<Unit> loop() {
            if (cursor.hasNext()) {
                return (cursor.next()).flatMap(this);
            } else if (errors.isEmpty()) {
                return U;
            } else {
                return raise(composeErrors(errors.getHead(), errors.getTail()));
            }
        }

        @Override
        public IO<Unit> recover(Throwable cause) {

            synchronized (cursor) {
                this.errors = errors.prepend(cause);
            }

            return loop();

        }
    }

}
