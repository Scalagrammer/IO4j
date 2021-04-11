package scg.io4j.utils;

import scg.io4j.IO;

@FunctionalInterface
public interface Extractable<A> {
    <X> IO<X> extractBy(TFunction<A, IO<X>> f);
}
