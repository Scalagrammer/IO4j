package scg.io4j;

import scg.io4j.utils.Extractable3;
import scg.io4j.utils.Product3;
import scg.io4j.utils.TFunction3;

import static scg.io4j.IO.pure;

@FunctionalInterface
public interface Extract3<A, B, C> extends IO<Extractable3<A, B, C>> {

    default IO<Product3<A, B, C>> product() {
        return this.unapply(Product3::apply);
    }

    default <R> IO<R> unapply(TFunction3<A, B, C, R> f) {
        return this.flatMap(e -> e.extractBy((a, b, c) -> pure(f.applyT(a, b, c))));
    }

    default <R> IO<R> flatUnapply(TFunction3<A, B, C, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f));
    }

    default <R> IO<Extractable3<A, B, C>> flatUnapplyTap(TFunction3<A, B, C, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f).as(e));
    }

}
