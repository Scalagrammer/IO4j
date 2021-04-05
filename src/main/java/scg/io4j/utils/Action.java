package scg.io4j.utils;

import io.atlassian.fugue.Either;
import io.atlassian.fugue.Unit;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Unit.VALUE;

@FunctionalInterface
public interface Action extends Attempt<Unit> {

    void perform() throws Throwable;

    @Override
    default Either<Throwable, Unit> attempt()  {
        try {
            this.perform();
            return right(VALUE);
        } catch (Throwable cause) {
            return left(cause);
        }
    }

}
