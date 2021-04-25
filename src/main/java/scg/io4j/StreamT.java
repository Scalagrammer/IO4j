package scg.io4j;

import io.atlassian.fugue.Monoid;
import io.atlassian.fugue.Option;
import io.atlassian.fugue.Unit;
import lombok.RequiredArgsConstructor;
import scg.io4j.utils.TFunction;
import scg.io4j.utils.TPredicate;

import java.util.*;
import java.util.function.*;

import java.util.stream.Collector;
import java.util.stream.Stream;

import static io.atlassian.fugue.Option.fromOptional;
import static java.util.stream.Collectors.*;
import static java.util.stream.Stream.*;
import static scg.io4j.IO.*;

@RequiredArgsConstructor
public class StreamT<R> {

    private static final StreamT<Object> empty = streamT(Stream.empty());

    public final IO<Stream<R>> value;

    public <RR> StreamT<RR> map(TFunction<R, RR> f) {
        return streamT(value.map(s -> s.map(f)));
    }

    public <RR> StreamT<RR> flatMapM(TFunction<R, IO<Stream<RR>>> f) {
        return streamT(value.flatMap(s -> s.map(f).reduce(StreamT.<RR>empty().value, (a, b) -> a.flatMap(as -> b.map(bs -> concat(as, bs))))));
    }

    public <RR> StreamT<RR> flatMap(TFunction<R, StreamT<RR>> f) {
        return flatMapM(item -> f.apply(item).value);
    }

    public <RR> StreamT<RR> semiflatMap(TFunction<R, IO<RR>> f) {
        return flatMap(value -> lift(f.apply(value)));
    }

    public <RR> StreamT<RR> subflatMap(TFunction<R, Stream<RR>> f) {
        return streamT(value.map(s -> s.flatMap(f)));
    }

    public StreamT<R> filter(TPredicate<R> p) {
        return streamT(value.map(s -> s.filter(p)));
    }

    public StreamT<R> filterNot(TPredicate<R> p) {
        return filter(p.negateT());
    }

    public StreamT<R> limit(long maxSize) {
        return streamT(value.map(s -> s.limit(maxSize)));
    }

    public IO<Stream<R>> toIO() {
        return this.value;
    }

    public IO<Option<R>> min(Comparator<R> comparator) {
        return this.value.map(s -> fromOptional(s.min(comparator)));
    }

    public IO<Option<R>> max(Comparator<R> comparator) {
        return this.value.map(s -> fromOptional(s.max(comparator)));
    }

    public StreamT<R> dropWhile(TPredicate<R> p) {
        return streamT(value.map(s -> s.dropWhile(p)));
    }

    public StreamT<R> takeWhile(TPredicate<R> p) {
        return streamT(value.map(s -> s.takeWhile(p)));
    }

    public IO<Long> size() {
        return this.collect(counting());
    }

    public IO<Boolean> nonEmpty() {
        return this.size().map(s -> (s > 0L));
    }

    public IO<Boolean> isEmpty() {
        return this.size().map(s -> (s == 0L));
    }

    public IO<Option<R>> find(TPredicate<R> p) {
        return this.value.map(s -> fromOptional(s.filter(p).findFirst()));
    }

    public IO<Boolean> forall(TPredicate<R> p) {
        return this.value.map(s -> s.allMatch(p));
    }

    public IO<Boolean> exists(TPredicate<R> p) {
        return this.value.map(s -> s.anyMatch(p));
    }

    public IO<Unit> foreach(Consumer<R> action) {
        return this.value.flatMap(s -> unit(() -> s.forEach(action)));
    }

//    public <A> IO<Map<A, List<R>>> groupBy(TFunction<R, A> classifier) {
//        return this.collect(groupingBy(classifier));
//    }

    public <A, RR> IO<RR> collect(Collector<R, A, RR> collector) {
        return this.value.map(s -> s.collect(collector));
    }

    public IO<R> foldMonoid(Monoid<R> monoid) {
        return this.collect(reducing(monoid.zero(), monoid::append));
    }

    @SuppressWarnings("unchecked")
    public static <V> StreamT<V> empty() {
        return ((StreamT<V>) empty);
    }

    public static <V> StreamT<V> empty(Class<V> typeTag) {
        return empty();
    }

    public static <T> StreamT<T> lift(IO<T> thunk) {
        return streamT(thunk.map(Stream::of));
    }

    public static <T> StreamT<T> streamT(T[] items) {
        return streamT(IO.pure(items).map(Stream::of));
    }

    public static <T> StreamT<T> streamT(Stream<T> s) {
        return streamT(IO.pure(s));
    }

    public static <T> StreamT<T> streamT(Collection<T> items) {
        return streamT(IO.pure(items).map(Collection::stream));
    }

    public static <T> StreamT<T> generate(Supplier<T> supply) {
        return streamT(Stream.generate(supply));
    }

    public static <T> StreamT<T> streamT(IO<Stream<T>> thunk) {
        return new StreamT<>(thunk);
    }

    public static <V> StreamT<V> pure(V head, V...tail) {
        return streamT(IO.pure(concat(of(head), of(tail))));
    }

}
