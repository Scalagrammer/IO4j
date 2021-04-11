package scg.io4j;

import scg.io4j.utils.Extractable2;
import scg.io4j.utils.Product2;
import scg.io4j.utils.TFunction2;

import static scg.io4j.IO.pure;

@FunctionalInterface
public interface Extract2<A, B> extends IO<Extractable2<A, B>> {

    default IO<Product2<A, B>> product() {
        return this.unapply(Product2::apply);
    }

    default <R> IO<R> unapply(TFunction2<A, B, R> f) {
        return this.flatMap(e -> e.extractBy((a, b) -> pure(f.apply(a, b))));
    }

    default <R> IO<R> flatUnapply(TFunction2<A, B, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f));
    }

    default <R> IO<Extractable2<A, B>> flatUnapplyTap(TFunction2<A, B, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f).as(e));
    }

}
