package scg.io4j;

import static java.lang.String.format;

public class MatchError extends Throwable {

    public MatchError(Object arg) {
        super(format("Cannot match argument %s", arg));
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

}
