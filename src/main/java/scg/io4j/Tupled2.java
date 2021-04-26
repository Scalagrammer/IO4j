package scg.io4j;

import scg.io4j.utils.TFunction2;

@FunctionalInterface
public interface Tupled2<A, B> {
    <R> IO<R> bind(TFunction2<A, B, IO<R>> f);
}
