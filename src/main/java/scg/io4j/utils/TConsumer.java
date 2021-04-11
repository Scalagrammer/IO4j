package scg.io4j.utils;

import lombok.SneakyThrows;

import java.util.function.Consumer;

@FunctionalInterface
public interface TConsumer<A> extends Consumer<A> {

    void acceptT(A arg) throws Throwable;

    @Override
    @SneakyThrows
    default void accept(A arg) {
        this.acceptT(arg);
    }

}
