package scg.io4j.utils;

import io.atlassian.fugue.Either;

@FunctionalInterface
public interface AttemptCapable<R> {
    Either<Throwable, R> attempt();
}
