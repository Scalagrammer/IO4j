package scg.io4j.utils;

import scg.io4j.IO;

@FunctionalInterface
public interface Extractable4<A, B, C, D> {
    <X> IO<X> extractBy(TFunction4<A, B, C, D, IO<X>> f);
}
