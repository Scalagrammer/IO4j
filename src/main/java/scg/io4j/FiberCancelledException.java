package scg.io4j;

class FiberCancelledException extends Throwable {

    static final FiberCancelledException FIBER_CANCELLED_EXCEPTION = new FiberCancelledException();

    private FiberCancelledException() {
        super();
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

}
