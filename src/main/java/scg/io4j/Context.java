package scg.io4j;

import io.atlassian.fugue.Option;

import lombok.val;

import java.util.Map;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

import static io.atlassian.fugue.Option.option;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.joining;

public interface Context {

    Context empty = fromMap(Map.of());

    boolean isEmpty();

    boolean contains(Object key);

    Map<Object, Object> toMap();

    <V> V get(Object key);

    void forEach(BiConsumer<Object, Object> action);

    default boolean nonEmpty() {
        return !(isEmpty());
    }

    default Context merge(Context overridable) {

        if (empty == this) {
            return overridable;
        }

        if (empty == overridable) {
            return this;
        }

        val updatedContext = new HashMap<>();

        overridable.forEach(updatedContext::put);

        this.forEach(updatedContext::put);

        return fromMap(updatedContext);

    }

    default <V> Option<V> find(Object key) {
        return option(get(key));
    }

    static Context fromMap(Map<Object, Object> context) {
        return new Context() {

            @Override
            public Map<Object, Object> toMap() {
                return Map.copyOf(context);
            }

            @Override
            public boolean contains(Object key) {
                return context.containsKey(key);
            }

            @Override
            public boolean isEmpty() {
                return context.isEmpty();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <V> V get(Object key) {

                Object value = context.get(key);

                if (isNull(value)) {
                    throw new NoSuchElementException(format("key: %s", key));
                }

                return (V) value;

            }

            @Override
            public String toString() {

                if (empty == this) {
                    return ("Context()");
                }

                return ((context.entrySet()).stream()).map(e -> (e.getKey()) + (" -> ") + (e.getValue())).collect(joining(", ", "Context(", ")"));

            }

            @Override
            public int hashCode() {
                return context.hashCode();
            }

            @Override
            public boolean equals(Object that) {
                return (this == that) || ((that instanceof Context) && context.equals(((Context) that).toMap()));
            }

            @Override
            public void forEach(BiConsumer<Object, Object> action) {
                context.forEach(action);
            }

        };
    }

}
