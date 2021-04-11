package scg.io4j.utils;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public final class NamingThreadFactory implements ThreadFactory {

    private final AtomicLong counter = new AtomicLong(0L);

    private final String namePattern;

    private final boolean daemon;

    @Override
    public Thread newThread(Runnable task) {

        Thread thread = new Thread(task);

        thread.setName(nextThreadName());

        thread.setDaemon(daemon);

        return thread;
    }

    private String nextThreadName() {
        return String.format(namePattern, counter.incrementAndGet());
    }

}
