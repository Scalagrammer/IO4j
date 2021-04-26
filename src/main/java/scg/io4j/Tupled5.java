package scg.io4j;

import scg.io4j.utils.TFunction5;

@FunctionalInterface
public interface Tupled5<A, B, C, D, E> {
    <R> IO<R> bind(TFunction5<A, B, C, D, E, IO<R>> f);
}
