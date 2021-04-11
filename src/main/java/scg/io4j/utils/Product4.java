package scg.io4j.utils;

import lombok.Value;
import scg.io4j.IO;

public interface Product4<A, B, C, D> extends Extractable4<A, B, C, D> {

    A get_1();
    B get_2();
    C get_3();
    D get_4();

    @Override
    default <X> IO<X> extractBy(TFunction4<A, B, C, D, IO<X>> f) {
        return f.apply(get_1(), get_2(), get_3(), get_4());
    }

    static <A, B, C, D> Product4<A, B, C, D> apply(A a, B b, C c, D d) {
        return new Product4Impl<>(a, b, c, d);
    }

}

@Value
class Product4Impl<A, B, C, D> implements Product4<A, B, C, D> {
    A _1;
    B _2;
    C _3;
    D _4;
}
