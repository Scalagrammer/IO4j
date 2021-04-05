package scg.io4j;

import io.atlassian.fugue.Option;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import static io.atlassian.fugue.Option.none;
import static io.atlassian.fugue.Option.option;

public final class Handler implements Iterable<Throwable> {

    private final AtomicReference<Option<Throwable>> cause = new AtomicReference<>(none());

    void put(Throwable cause) {
        this.cause.set(option(cause));
    }

    public boolean isEmpty() {
        return (cause.get()).isEmpty();
    }

    @Override
    public Iterator<Throwable> iterator() {
        return (cause.get()).iterator();
    }

}
