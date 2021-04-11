package scg.io4j.utils;

import lombok.Value;
import scg.io4j.IO;

public interface Product2<A, B> extends Extractable2<A, B> {

    A get_1();
    B get_2();

    @Override
    default <X> IO<X> extractBy(TFunction2<A, B, IO<X>> f) {
        return f.apply(get_1(), get_2());
    }

    static <A, B> Product2<A, B> apply(A a, B b) {
        return new Product2Impl<>(a, b);
    }

}

@Value
class Product2Impl<A, B> implements Product2<A, B> {
    A _1;
    B _2;
}
