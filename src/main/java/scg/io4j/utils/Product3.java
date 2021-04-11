package scg.io4j.utils;

import lombok.Value;
import scg.io4j.IO;

public interface Product3<A, B, C> extends Extractable3<A, B, C> {

    A get_1();
    B get_2();
    C get_3();

    @Override
    default <X> IO<X> extractBy(TFunction3<A, B, C, IO<X>> f) {
        return f.apply(get_1(), get_2(), get_3());
    }

    static <A, B, C> Product3<A, B, C> apply(A a, B b, C c) {
        return new Product3Impl<>(a, b, c);
    }

}

@Value
class Product3Impl<A, B, C> implements Product3<A, B, C> {
    A _1;
    B _2;
    C _3;
}
