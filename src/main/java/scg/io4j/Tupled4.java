package scg.io4j;

import scg.io4j.utils.TFunction4;

@FunctionalInterface
public interface Tupled4<A, B, C, D> {
    <R> IO<R> bind(TFunction4<A, B, C, D, IO<R>> f);
}
