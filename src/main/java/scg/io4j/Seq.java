package scg.io4j;

import io.atlassian.fugue.Either;
import io.atlassian.fugue.Monoid;
import io.atlassian.fugue.Option;
import lombok.RequiredArgsConstructor;
import lombok.val;
import scg.io4j.utils.TBinaryOperator;
import scg.io4j.utils.TConsumer;
import scg.io4j.utils.TFunction;

import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Option.fromOptional;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static scg.io4j.IO.*;

public interface Seq<R> extends IO<Stream<R>> {

    default IO<R> traverse() {
        return callback -> {

            TConsumer<Either<Throwable, Stream<R>>> f = attempt -> {
                if (attempt.isLeft()) {
                    (attempt.left()).forEach(cause -> callback.accept(left(cause)));
                } else for (Stream<R> s : attempt.right()) {
                    s.forEach(result -> callback.accept(right(result)));
                }
            };

            this.run(f);

        };
    }

    default IO<R> fold(Monoid<R> monoid) {
        return this.map(s -> s.reduce(monoid.zero(), monoid::append));
    }

    default IO<R> fold(R zero, TBinaryOperator<R> f) {
        return this.map(s -> s.reduce(zero, f));
    }

    default IO<Option<R>> reduce(TBinaryOperator<R> f) {
        return this.map(s -> fromOptional(s.reduce(f)));
    }

    default  <RR> IO<RR> foldMap(Monoid<RR> monoid, TFunction<R, RR> f) {
        return this.map(s -> s.reduce(monoid.zero(), (rr, r) -> monoid.append(rr, f.apply(r)), monoid::append));
    }

}

@RequiredArgsConstructor
@SuppressWarnings({"rawtypes", "unchecked"})
final class SeqImpl<R> implements Seq<R> {

    private static final IO zero = pure(empty());

    private static final BinaryOperator concat = (a, b) -> {
        return ((IO) a).flatMap(as -> ((IO) b).map(bs -> concat(((Stream) as), ((Stream) bs))));
    };

    private final Stream<IO<R>> value;

    @Override
    public void run(TConsumer<Either<Throwable, Stream<R>>> callback) {
        this.value.map(e -> e.map(Stream::of)).reduce(((IO<Stream<R>>) zero), ((BinaryOperator<IO<Stream<R>>>) concat)).run(callback);
    }
}