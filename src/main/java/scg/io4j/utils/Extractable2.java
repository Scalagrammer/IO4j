package scg.io4j.utils;

import scg.io4j.IO;

@FunctionalInterface
public interface Extractable2<A, B> {
    <X> IO<X> extractBy(TFunction2<A, B, IO<X>> f);
}