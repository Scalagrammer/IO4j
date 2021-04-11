package scg.io4j;

import io.atlassian.fugue.Either;
import lombok.RequiredArgsConstructor;
import scg.io4j.utils.TFunction;

import static scg.io4j.utils.TFunctions.identityT;

@RequiredArgsConstructor
public class EitherT<L, R> {

    public final IO<Either<L, R>> value;

    public <LL, RR> EitherT<LL, RR> bimap(TFunction<L, LL> fl, TFunction<R, RR> fr) {
        return eitherT(value.map(e -> e.bimap(fl, fr)));
    }

    public <RR> EitherT<L, RR> map(TFunction<R, RR> f) {
        return bimap(identityT(), f);
    }

    public <RR> EitherT<L, RR> flatMap(TFunction<R, EitherT<L, RR>> f) {
        return eitherT(value.flatMap(e -> e.fold(l -> IO.pure(Either.left(l)), (r) -> f.apply(r).value)));
    }

    public <RR> EitherT<L, RR> subflatMap(TFunction<R, Either<L, RR>> f) {
        return transform(e -> e.flatMap(f));
    }

    public <RR> EitherT<L, RR> semiflatMap(TFunction<R, IO<RR>> f) {
        return flatMap(f.andThenT(EitherT::liftM));
    }

    public <RR> EitherT<L, RR> flatMapM(TFunction<R, IO<Either<L, RR>>> f) {
        return flatMap(f.andThenT(EitherT::new));
    }

    public <LL> EitherT<LL, R> leftMap(TFunction<L, LL> f) {
        return bimap(f, identityT());
    }

    public <LL> EitherT<LL, R> leftSemiflatMap(TFunction<L, IO<LL>> f) {
        return eitherT(value.flatMap(e -> e.fold(l -> f.apply(l).map(Either::left), r -> IO.pure(Either.right(r)))));
    }

    public <LL, RR> EitherT<LL, RR> transform(TFunction<Either<L, R>, Either<LL, RR>> f) {
        return eitherT(value.map(f));
    }

    public <RR> IO<RR> foldM(TFunction<L, IO<RR>> lf, TFunction<R, IO<RR>> rf) {
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
