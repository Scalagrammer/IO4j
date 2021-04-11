package scg.io4j.utils;

import lombok.Value;
import scg.io4j.IO;

public interface Product5<A, B, C, D, E> extends Extractable5<A, B, C, D, E> {

    A get_1();
    B get_2();
    C get_3();
    D get_4();
    E get_5();

    @Override
    default <X> IO<X> extractBy(TFunction5<A, B, C, D, E, IO<X>> f) {
        return f.apply(get_1(), get_2(), get_3(), get_4(), get_5());
    }

    static <A, B, C, D, E> Product5<A, B, C, D, E> apply(A a, B b, C c, D d, E e) {
        return new Product5Impl<>(a, b, c, d, e);
    }

}

@Value
class Product5Impl<A, B, C, D, E> implements Product5<A, B, C, D, E> {
    A _1;
    B _2;
    C _3;
    D _4;
    E _5;
}
