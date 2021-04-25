package scg.io4j.utils;

import lombok.SneakyThrows;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface TBiConsumer<A, B> extends BiConsumer<A, B> {

    void acceptT(A a, B b) throws Throwable;

    @Override
    @SneakyThrows
    default void accept(A a, B b) {
        this.acceptT(a, b);
    }

}
