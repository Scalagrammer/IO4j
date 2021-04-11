package scg.io4j.utils;

import lombok.SneakyThrows;

@FunctionalInterface
public interface TFunction5<A, B, C, D, E, R> {

    R applyT(A a, B b, C c, D d, E e) throws Throwable;

    @SneakyThrows
    default R apply(A a, B b, C c, D d, E e) {
        return this.applyT(a, b, c, d, e);
    }

    default TFunction<Product5<A, B, C, D, E>, R> tupled() {
        return p -> apply(p.get_1(), p.get_2(), p.get_3(), p.get_4(), p.get_5());
    }

}
