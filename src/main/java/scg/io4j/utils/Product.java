package scg.io4j.utils;

import lombok.Value;
import scg.io4j.IO;

@FunctionalInterface
public interface Product<A> extends Extractable<A> {

    A get_1();

    @Override
    default <X> IO<X> extractBy(TFunction<A, IO<X>> f) {
        return f.apply(get_1());
    }

    static <A> Product<A> apply(A a) {
        return new ProductImpl<>(a);
    }

}

@Value
class ProductImpl<A> implements Product<A> {
    A _1;
}
