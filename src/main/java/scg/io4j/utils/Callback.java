package scg.io4j.utils;

import java.util.concurrent.atomic.AtomicBoolean;

public interface Callback<R> {

    void success(R result);
    void failure(Throwable cause);

    static <R> Callback<R> once(Callback<R> delegate) {
        return new Callback<>() {

            final AtomicBoolean done = new AtomicBoolean(false);

            @Override
            public void success(R result) {
                if (done.compareAndSet(false, true)) {
                    delegate.success(result);
                }
            }

            @Override
            public void failure(Throwable cause) {
                if (done.compareAndSet(false, true)) {
                    delegate.failure(cause);
                }
            }
        };
    }

}
