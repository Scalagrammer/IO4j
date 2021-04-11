package scg.io4j.utils;

import scg.io4j.IO;

@FunctionalInterface
public interface Extractable3<A, B, C> {
    <X> IO<X> extractBy(TFunction3<A, B, C, IO<X>> f);
}
