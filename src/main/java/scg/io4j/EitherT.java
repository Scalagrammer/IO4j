package scg.io4j;

import io.atlassian.fugue.Either;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

import static java.util.function.Function.identity;

@RequiredArgsConstructor
public class EitherT<L, R> {

    public final IO<Either<L, R>> value;

    public <LL, RR> EitherT<LL, RR> bimap(Function<L, LL> fl, Function<R, RR> fr) {
        return eitherT(value.map(e -> e.bimap(fl, fr)));
    }

    public <RR> EitherT<L, RR> map(Function<R, RR> f) {
        return bimap(identity(), f);
    }

    public <RR> EitherT<L, RR> flatMap(Function<R, EitherT<L, RR>> f) {
        return eitherT(value.flatMap(e -> e.fold(l -> IO.pure(Either.left(l)), (r) -> f.apply(r).value)));
    }

    public <RR> EitherT<L, RR> subflatMap(Function<R, Either<L, RR>> f) {
        return transform(e -> e.flatMap(f));
    }

    public <RR> EitherT<L, RR> semiflatMap(Function<R, IO<RR>> f) {
        return flatMap(f.andThen(EitherT::liftM));
    }

    public <RR> EitherT<L, RR> flatMapM(Function<R, IO<Either<L, RR>>> f) {
        return flatMap(f.andThen(EitherT::new));
    }

    public <LL> EitherT<LL, R> leftMap(Function<L, LL> f) {
        return bimap(f, identity());
    }

//    public <LL> EitherT<LL, R> leftFlatMap(Function<L, EitherT<LL, R>> f) {
//        return eitherT(value.flatMap(e -> e.fold(f.andThen(EitherT::toIO), r -> pure(Either.right(r)))));
//    }

    public <LL> EitherT<LL, R> leftSemiflatMap(Function<L, IO<LL>> f) {
        return eitherT(value.flatMap(e -> e.fold(l -> f.apply(l).map(Either::left), r -> IO.pure(Either.right(r)))));
    }

    public <LL, RR> EitherT<LL, RR> transform(Function<Either<L, R>, Either<LL, RR>> f) {
        return eitherT(value.map(f));
    }

    public <RR> IO<RR> foldM(Function<L, IO<RR>> lf, Function<R, IO<RR>> rf) {
        return this.value.flatMap(e -> e.fold(lf, rf));
    }

    public IO<Either<L, R>> toIO() {
        return this.value;
    }

    public static <L, R>  EitherT<L, R> leftT(L value) {
        return eitherT(IO.pure(value).map(Either::left));
    }

    public static <L, R>  EitherT<L, R> left(IO<L> thunk) {
        return eitherT(thunk.map(Either::left));
    }

    public static <L, R>  EitherT<L, R> pure(R value) {
        return eitherT(IO.pure(Either.right(value)));
    }

    public static <L, R>  EitherT<L, R> liftM(IO<R> thunk) {
        return eitherT(thunk.map(Either::right));
    }

    public static <L, R>  EitherT<L, R> eitherT(Either<L, R> value) {
        return eitherT(IO.pure(value));
    }

    public static <L, R>  EitherT<L, R> eitherT(IO<Either<L, R>> thunk) {
        return new EitherT<>(thunk);
    }

}
