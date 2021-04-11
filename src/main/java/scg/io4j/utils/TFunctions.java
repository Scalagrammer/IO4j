package scg.io4j.utils;

import io.atlassian.fugue.Pair;

@SuppressWarnings({"unchecked", "rawtypes"})
public class TFunctions {

    private static final TFunction ID = any -> any;

    private TFunctions() {
        throw new UnsupportedOperationException();
    }

    public static <A> TFunction<A, A> identityT() {
        return ID;
    }

    public static <A, R> TFunction<A, R> untupled(TFunction<Product<A>, R> f) {
        return a -> f.apply(Product.apply(a));
    }

    public static <A, B, R> TFunction2<A, B, R> untupled2(TFunction<Product2<A, B>, R> f) {
        return (a, b) -> f.apply(Product2.apply(a, b));
    }

    public static <A, B, C, R> TFunction3<A, B, C, R> untupled3(TFunction<Product3<A, B, C>, R> f) {
        return (a, b, c) -> f.apply(Product3.apply(a, b, c));
    }

    public static <A, B, C, D, R> TFunction4<A, B, C, D, R> untupled4(TFunction<Product4<A, B, C, D>, R> f) {
        return (a, b, c, d) -> f.apply(Product4.apply(a, b, c, d));
    }

    public static <A, B, C, D, E, R> TFunction5<A, B, C, D, E, R> untupled5(TFunction<Product5<A, B, C, D, E>, R> f) {
        return (a, b, c, d, e) -> f.apply(Product5.apply(a, b, c, d, e));
    }

    public static <A, R> TFunction<TFunction<A, R>, R> apply(A a) {
        return f -> f.applyT(a);
    }

    public static <A, R> TFunction<TFunction<A, R>, R> apply(TSupplier<A> a) {
        return f -> f.apply(a.getT());
    }

    public static <A, B, R> TFunction<TFunction2<A, B, R>, R> apply(A a, TSupplier<B> b) {
        return f -> f.apply(a, b.getT());
    }

    public static <A, B, R> TFunction<TFunction2<A, B, R>, R> apply(TSupplier<A> a, B b) {
        return f -> f.apply(a.getT(), b);
    }

    public static <A, B, R> TFunction<TFunction2<A, B, R>, R> apply(TSupplier<A> a, TSupplier<B> b) {
        return f -> f.apply(a.getT(), b.getT());
    }

    public static <A, R> TFunction<A, R> constant(R value) {
        return a -> value;
    }
}
