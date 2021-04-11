package scg.io4j;

import scg.io4j.utils.*;

@FunctionalInterface
public interface Extract<A> extends IO<Extractable<A>> {

    default IO<Product<A>> product() {
        return this.unapply(Product::apply);
    }

    default <R> IO<R> unapply(TFunction<A, R> f) {
        return this.flatMap(e -> e.extractBy(f.andThenT(IO::pure)));
    }

    default <R> IO<R> flatUnapply(TFunction<A, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f));
    }

    default <R> IO<Extractable<A>> flatUnapplyTap(TFunction<A, IO<R>> f) {
        return this.flatMap(e -> e.extractBy(f).as(e));
    }

}
