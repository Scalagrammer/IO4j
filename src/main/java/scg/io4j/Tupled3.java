package scg.io4j;

import scg.io4j.utils.TFunction3;

@FunctionalInterface
public interface Tupled3<A, B, C> {
    <R> IO<R> bind(TFunction3<A, B, C, IO<R>> f);
}
