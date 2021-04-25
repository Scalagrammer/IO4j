package scg.io4j;

import lombok.RequiredArgsConstructor;
import scg.io4j.IO.IOFrame;
import scg.io4j.utils.TFunction;

import static scg.io4j.IO.pure;

@RequiredArgsConstructor
public final class ErrorHandler<A> extends IOFrame<A, IO<A>> {

    private final TFunction<Throwable, IO<A>> h;

    @Override
    public IO<A> applyT(A a) {
        return pure(a);
    }

    @Override
    public IO<A> recover(Throwable cause) {
        return this.h.apply(cause);
    }
}
