package scg.io4j;

import io.atlassian.fugue.Maybe;
import io.atlassian.fugue.Option;
import io.atlassian.fugue.Pair;
import lombok.RequiredArgsConstructor;
import scg.io4j.utils.TFunction;
import scg.io4j.utils.TPredicate;
import scg.io4j.utils.TSupplier;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.atlassian.fugue.Option.option;
import static io.atlassian.fugue.Option.some;
import static io.atlassian.fugue.Pair.pair;

@RequiredArgsConstructor
public class OptionT<R> {

    private static final OptionT<?> none = optionT(Option.none());

    public final IO<Option<R>> value;

    public <RR> OptionT<RR> map(TFunction<R, RR> f) {
        return optionT(value.map(maybe -> maybe.map(f)));
    }

    public <RR> OptionT<RR> flatMapM(TFunction<R, IO<Option<RR>>> f) {
        return optionT(value.flatMap(maybe -> maybe.fold(() -> OptionT.<RR>none().value, f)));
    }

    public <RR> OptionT<RR> flatMap(TFunction<R, OptionT<RR>> f) {
        return flatMapM(value -> f.apply(value).value);
    }

    public <RR> OptionT<RR> semiflatMap(TFunction<R, IO<RR>> f) {
        return flatMap(value -> lift(f.apply(value)));
    }

    public <RR> OptionT<RR> subflatMap(TFunction<R, Option<RR>> f) {
        return optionT(value.map(opt -> opt.flatMap(f)));
    }

    public IO<R> orM(IO<R> defaultValue) {
        return this.value.flatMap(maybe -> maybe.fold(() -> defaultValue, IO::pure));
    }

    public IO<R> orElse(IO<R> defaultValue) {
        return orM(defaultValue);
    }

    public IO<R> or(R defaultValue) {
        return this.value.map(opt -> opt.getOrElse(defaultValue));
    }

    public IO<R> or(TSupplier<R> defaultValue) {
        return this.value.map(opt -> opt.getOrElse(defaultValue.get()));
    }

    public IO<R> orGetM(TSupplier<IO<R>> defaultValue) {
        return this.value.flatMap(maybe -> maybe.fold(defaultValue, IO::pure));
    }

    public IO<R> orElse(TSupplier<IO<R>> defaultValue) {
        return orGetM(defaultValue);
    }

    public IO<R> orEmpty() {
        return this.value.flatMap(maybe -> maybe.fold(IO::never, IO::pure));
    }

    public <A> OptionT<Pair<R, A>> zipWith(Option<A> maybeA) {
        return this.subflatMap(r -> maybeA.map(a -> pair(r, a)));
    }

    public <T> OptionT<T> cast(Class<T> clazz) {
        return this.map(clazz::cast);
    }

    public <RR> RR as(TFunction<IO<Option<R>>, RR> f) {
        return f.apply(value);
    }

    public OptionT<R> filter(TPredicate<R> p) {
        return optionT(value.map(opt -> opt.filter(p)));
    }

    public OptionT<R> filterNot(TPredicate<R> p) {
        return filter(p.negateT());
    }

    public OptionT<R> orT(OptionT<R> defaultValue) {
        return optionT(value.flatMap(maybe -> maybe.fold(() -> defaultValue.value, v -> IO.pure(some(v)))));
    }

    public OptionT<R> orGetT(Supplier<OptionT<R>> defaultValue) {
        return optionT(value.flatMap(maybe -> maybe.fold(() -> (defaultValue.get()).value, v -> IO.pure(some(v)))));
    }

    public OptionT<R> otherwise(IO<Option<R>> defaultValue) {
        return optionT(value.flatMap(maybe -> maybe.fold(() -> defaultValue, v -> IO.pure(some(v)))));
    }

    public IO<Boolean> isEmpty() {
        return this.value.map(Maybe::isEmpty);
    }

    public IO<Boolean> isDefined() {
        return this.value.map(Maybe::isDefined);
    }

    public IO<Boolean> forall(Predicate<R> p) {
        return this.value.map(maybe -> maybe.forall(p));
    }

    public IO<Option<R>> toIO() {
        return this.value;
    }

    @SuppressWarnings("unchecked")
    public static <V> OptionT<V> none() {
        return ((OptionT<V>) none);
    }

    public static <V> OptionT<V> pure(V nullable) {
        return optionT(IO.pure(option(nullable)));
    }

    public static <T> OptionT<T> lift(IO<T> thunk) {
        return optionT(thunk.map(Option::some));
    }

    public static <T> OptionT<T> optionT(Option<T> maybe) {
        return maybe.isEmpty() ? none() : optionT(IO.pure(maybe));
    }

    public static <T> OptionT<T> optionT(IO<Option<T>> thunk) {
        return new OptionT<T>(thunk);
    }

}