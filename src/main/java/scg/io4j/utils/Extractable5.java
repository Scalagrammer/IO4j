package scg.io4j.utils;

import scg.io4j.IO;

@FunctionalInterface
public interface Extractable5<A, B, C, D, E> {
    <X> IO<X> extractBy(TFunction5<A, B, C, D, E, IO<X>> f);
}
