package scg.io4j;

import io.atlassian.fugue.Option;

import java.util.Iterator;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.atlassian.fugue.Option.none;
import static io.atlassian.fugue.Option.option;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.isEqual;

public final class ProgressCheck implements Iterable<ProgressStatus>, Predicate<ProgressStatus> {

    private final AtomicReference<Option<ProgressStatus>> status = new AtomicReference<>(none());

    @Override
    public Iterator<ProgressStatus> iterator() {
        return this.map(Option::iterator);
    }

    @Override
    public boolean test(ProgressStatus s) {
        return this.is(s);
    }

    public Option<ProgressStatus> now() {
        return this.map(identity());
    }

    public boolean is(ProgressStatus s) {
        return this.map(maybe -> maybe.exists(isEqual(s)));
    }

    void update(ProgressStatus s) {
        this.status.set(option(s));
    }

    private <R> R map(Function<Option<ProgressStatus>, R> f) {
        return f.apply(status.get());
    }

}
