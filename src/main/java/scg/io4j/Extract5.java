package scg.io4j;

import scg.io4j.utils.*;

import static scg.io4j.IO.pure;

@FunctionalInterface
public interface Extract5<A, B, C, D, E> extends IO<Extractable5<A, B, C, D, E>> {

    default IO<Product5<A, B, C, D, E>> product() {
        return this.unapply(Product5::apply);
    }

    default <R> IO<R> unapply(TFunction5<A, B, C, D, E, R> f) {
        return this.flatMap(extractable -> extractable.extractBy((a, b, c, d, e) -> pure(f.apply(a, b, c, d, e))));
    }

    default <R> IO<R> flatUnapply(TFunction5<A, B, C, D, E, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f));
    }

    default <R> IO<Extractable5<A, B, C, D, E>> flatUnapplyTap(TFunction5<A, B, C, D, E, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f).as(e));
    }

}
