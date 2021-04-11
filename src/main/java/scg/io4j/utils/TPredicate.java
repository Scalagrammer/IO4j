package scg.io4j.utils;

import lombok.SneakyThrows;

import java.util.function.Predicate;

@FunctionalInterface
public interface TPredicate<A> extends Predicate<A> {

    boolean testT(A a) throws Throwable;

    @Override
    @SneakyThrows
    default boolean test(A a) {
        return this.testT(a);
    }

    default TPredicate<A> andT(TPredicate<? super A> other) {
        return a -> testT(a) && other.testT(a);
    }

    default TPredicate<A> negateT() {
        return a -> !testT(a);
    }

    default TPredicate<A> orT(TPredicate<? super A> other) {
        return a -> testT(a) || other.testT(a);
    }
}
