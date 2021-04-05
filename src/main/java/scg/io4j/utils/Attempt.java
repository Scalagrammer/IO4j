package scg.io4j.utils;

import io.atlassian.fugue.Either;

@FunctionalInterface
public interface Attempt<R> {
    Either<Throwable, R> attempt();
}
