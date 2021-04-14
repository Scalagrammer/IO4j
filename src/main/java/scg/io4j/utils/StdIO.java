package scg.io4j.utils;

import io.atlassian.fugue.Unit;
import scg.io4j.IO;

import java.io.Console;
import java.util.Formatter;

import static java.lang.String.valueOf;
import static scg.io4j.IO.pure;
import static scg.io4j.IO.unit;

public final class StdIO {

    private static final IO<Console> console = pure(System.console());

    private StdIO() {
        throw new UnsupportedOperationException();
    }

    public static IO<String> readln() {
        return null;
    }

    public static IO<Unit> println(Object line, Object... args) {
        return unit(() -> new Formatter(System.out).format(valueOf(line).concat("\n"), args).flush());
    }

}
