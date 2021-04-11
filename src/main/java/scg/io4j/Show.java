package scg.io4j;

import scg.io4j.utils.TFunction;

@FunctionalInterface
public interface Show<A> extends TFunction<A, String> {

    String show(A a);

    @Override
    default String applyT(A arg) {
        return this.show(arg);
    }

}
