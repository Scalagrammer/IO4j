package scg.io4j;

import io.atlassian.fugue.Either;
import io.atlassian.fugue.Unit;

import scg.io4j.utils.TConsumer;
import scg.io4j.utils.TFunction;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;

import static io.atlassian.fugue.Either.right;

public interface Task<R> extends IO<TFunction<Scheduler, IO<R>>> {
    IO<R> perform(Scheduler scheduler);
}

abstract class AbstractTask<R> implements Task<R> {
    @Override
    public final IO<R> perform(Scheduler s) {
        return this.flatMap(f -> f.apply(s));
    }
}

@RequiredArgsConstructor
final class TimeoutTask<R> extends AbstractTask<R> {

    private final long delay;
    private final TimeUnit unit;

    @Override
    public void run(TConsumer<Either<Throwable, TFunction<Scheduler, IO<R>>>> callback) {
        callback.accept(right(s -> s.timeout(delay, unit)));
    }
}

@RequiredArgsConstructor
final class SleepTask extends AbstractTask<Unit> {

    private final long delay;
    private final TimeUnit unit;

    @Override
    public void run(TConsumer<Either<Throwable, TFunction<Scheduler, IO<Unit>>>> callback) {
        callback.accept(right(s -> s.schedule(delay, unit)));
    }
}
