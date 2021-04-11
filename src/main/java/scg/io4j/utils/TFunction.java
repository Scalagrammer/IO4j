package scg.io4j.utils;

import lombok.SneakyThrows;

import java.util.function.Function;

@FunctionalInterface
public interface TFunction<A, R> extends Function<A, R> {

    R applyT(A arg) throws Throwable;

    @Override
    @SneakyThrows
    default R apply(A arg) {
        return this.applyT(arg);
    }

    default TFunction<Product<A>, R> tupled() {
        return p -> apply(p.get_1());
    }

    default <V> TFunction<V, R> composeT(TFunction<? super V, ? extends A> before) {
        return v -> apply(before.apply(v));
    }

    default <V> TFunction<A, V> andThenT(TFunction<? super R, ? extends V> after) {
        return a -> after.apply(apply(a));
    }

    static <A, R> TFunction<A, R> constantT(R result) {
        return ignored -> result;
    }

}
