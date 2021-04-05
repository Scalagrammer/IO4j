package scg.io4j.utils;

import io.atlassian.fugue.Either;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;

public interface AsyncCallback<R> {

    void success(R result);
    void failure(Throwable cause);

    static <R> AsyncCallback<R> wrap(Consumer<Either<Throwable, R>> f) {
        return new AsyncCallback<>() {

            final AtomicBoolean done = new AtomicBoolean(false);

            @Override
            public void success(R result) {
                if (done.compareAndSet(false, true)) {
                    f.accept(right(result));
                }
            }

            @Override
            public void failure(Throwable cause) {
                if (done.compareAndSet(false, true)) {
                    f.accept(left(cause));
                }
            }
        };
    }

}
