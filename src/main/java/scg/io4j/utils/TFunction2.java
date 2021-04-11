package scg.io4j.utils;

import lombok.SneakyThrows;

import java.util.function.BiFunction;

@FunctionalInterface
public interface TFunction2<A, B, R> extends BiFunction<A, B, R> {

    R applyT(A a, B b) throws Throwable; // throwable apply

    @Override
    @SneakyThrows
    default R apply(A a, B b) {
        return this.applyT(a, b);
    }

    default TFunction<Product2<A, B>, R> tupled() {
        return p -> apply(p.get_1(), p.get_2());
    }

}
