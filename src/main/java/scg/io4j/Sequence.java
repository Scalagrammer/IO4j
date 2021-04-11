package scg.io4j;

import io.atlassian.fugue.Either;
import io.atlassian.fugue.Monoid;
import io.atlassian.fugue.Option;
import lombok.RequiredArgsConstructor;
import scg.io4j.utils.Callable;
import scg.io4j.utils.TBinaryOperator;
import scg.io4j.utils.TConsumer;
import scg.io4j.utils.TFunction;

import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Option.fromOptional;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static scg.io4j.IO.*;

public interface Sequence<R> extends IO<Stream<IO<R>>> {

    IO<R> fold(Monoid<R> monoid);

    IO<R> fold(R zero, TBinaryOperator<R> f);

    IO<Option<R>> reduce(TBinaryOperator<R> f);

    <RR> IO<RR> foldMap(Monoid<RR> monoid, TFunction<R, RR> f);

}

@RequiredArgsConstructor
final class SequenceImpl<R> implements Sequence<R> {

    private final Stream<IO<R>> value;

    @Override
    public IO<R> fold(Monoid<R> monoid) {
        return sequence(value).map(s -> s.reduce(monoid.zero(), monoid::append));
    }

    @Override
    public IO<R> fold(R zero, TBinaryOperator<R> f) {
        return sequence(value).map(s -> s.reduce(zero, f));
    }

    @Override
    public IO<Option<R>> reduce(TBinaryOperator<R> f) {
        return sequence(value).map(s -> fromOptional(s.reduce(f)));
    }

    @Override
    public <RR> IO<RR> foldMap(Monoid<RR> monoid, TFunction<R, RR> f) {
        return sequence(value).map(s -> s.reduce(monoid.zero(), (rr, r) -> monoid.append(rr, f.apply(r)), monoid::append));
    }

    @Override
    public void run(TConsumer<Either<Throwable, Stream<IO<R>>>> callback) {
        callback.accept(right(value));
    }

    private static <R> IO<Stream<R>> sequence(Stream<IO<R>> value) {

        Callable<IO<Stream<R>>> f = () -> {

            BinaryOperator<IO<Stream<R>>> concat = (a, b) -> {
                return a.flatMap(as -> b.map(bs -> concat(as, bs)));
            };

            return value.map(e -> e.map(Stream::of)).reduce(pure(empty()), concat);

        };

        return suspend(f);

    }

}