package scg.io4j;

import scg.io4j.utils.*;

import static scg.io4j.IO.pure;

@FunctionalInterface
public interface Extract4<A, B, C, D> extends IO<Extractable4<A, B, C, D>> {

    default IO<Product4<A, B, C, D>> product() {
        return this.unapply(Product4::apply);
    }

    default <R> IO<R> unapply(TFunction4<A, B, C, D, R> f) {
        return this.flatMap(e -> e.extractBy((a, b, c, d) -> pure(f.apply(a, b, c, d))));
    }

    default <R> IO<R> flatUnapply(TFunction4<A, B, C, D, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f));
    }

    default <R> IO<Extractable4<A, B, C, D>> flatUnapplyTap(TFunction4<A, B, C, D, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f).as(e));
    }

}
