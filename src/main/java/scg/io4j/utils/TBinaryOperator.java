package scg.io4j.utils;

import lombok.SneakyThrows;

import java.util.function.BinaryOperator;

@FunctionalInterface
public interface TBinaryOperator<A> extends BinaryOperator<A> {

    A applyT(A a, A b) throws Throwable;

    @Override
    @SneakyThrows
    default A apply(A a, A b) {
        return this.applyT(a, b);
    }

}
