package scg.io4j.utils;

import lombok.SneakyThrows;

@FunctionalInterface
public interface TFunction3<A, B, C, R> {

    R applyT(A a, B b, C c) throws Throwable;

    @SneakyThrows
    default R apply(A a, B b, C c) {
        return this.applyT(a, b, c);
    }

    default TFunction<Product3<A, B, C>, R> tupled() {
        return p -> apply(p.get_1(), p.get_2(), p.get_3());
    }

}
