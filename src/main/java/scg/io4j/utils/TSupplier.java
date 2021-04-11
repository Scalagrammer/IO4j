package scg.io4j.utils;

import lombok.SneakyThrows;

import java.util.function.Supplier;

@FunctionalInterface
public interface TSupplier<R> extends Supplier<R> {

    R getT() throws Throwable;
    
    @Override
    @SneakyThrows
    default R get() {
        return this.getT();
    }
    
    static <R> TSupplier<R> ofInstanceT(R result) {
        return () -> result;
    }
    
}
