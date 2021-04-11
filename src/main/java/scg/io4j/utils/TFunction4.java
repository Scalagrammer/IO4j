package scg.io4j.utils;

import lombok.SneakyThrows;

@FunctionalInterface
public interface TFunction4<A, B, C, D, R> {

    R applyT(A a, B b, C c, D d) throws Throwable;

    @SneakyThrows
    default R apply(A a, B b, C c, D d) {
        return this.applyT(a, b, c, d);
    }

    default TFunction<Product4<A, B, C, D>, R> tupled() {
        return p -> apply(p.get_1(), p.get_2(), p.get_3(), p.get_4());
    }

}
