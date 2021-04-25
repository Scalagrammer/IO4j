package scg.io4j;

import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;

import java.util.Iterator;
import java.util.stream.Stream;

import static java.util.stream.Stream.of;
import static scg.io4j.FListImpl.NIL;

@SuppressWarnings("unchecked")
interface FList<R> extends Iterable<R> {

    R getHead();

    FList<R> getTail();

    default boolean isEmpty() {
        return (this == NIL);
    }

    default boolean nonEmpty() {
        return (this != NIL);
    }

    default FList<R> prepend(R head) {
        return cons(head, this);
    }

    @Override
    default Iterator<R> iterator() {
        return new Iterator<>() {

            FList<R> current = FList.this;

            @Override
            public boolean hasNext() {
                return this.current.nonEmpty();
            }

            @Override
            public R next() {

                val head = current.getHead();

                this.current = current.getTail();

                return head;

            }

        };
    }

    static <R> FList<R> single(R head) {
        return FListImpl.cons(head, nil());
    }

    static <R> FList<R> cons(R head, FList<R> tail) {
        return FListImpl.cons(head, tail);
    }

    default FList<R> concat(FList<R> last) {
        return cons(getHead(), (getTail()).concat(last));
    }

    default FList<R> append(R last) {
        return cons(getHead(), (getTail()).append(last));
    }

    static <R> FList<R> nil() {
        return NIL;
    }

    @SafeVarargs
    static <R> FList<R> collect(R head, R...tail) {

        FList<R> result = single(head);

        for (val next : tail) {
            result = result.append(next);
        }

        return result;

    }
}

@Value(staticConstructor = "cons")
@SuppressWarnings({"rawtypes", "unchecked"})
class FListImpl<R> implements FList<R> {

    R head;
    FList<R> tail;

    @Override
    public String toString() {
        return head + (" :: ") + tail;
    }

    static FList NIL = new FList() {

        @Override
        @SneakyThrows
        public Object getHead() {
            throw new NoSuchFieldException();
        }

        @Override
        @SneakyThrows
        public FList getTail() {
            throw new NoSuchFieldException();
        }

        @Override
        public FList append(Object head) {
            return this.prepend(head);
        }

        @Override
        public FList prepend(Object head) {
            return new FListImpl(head, this);
        }

        @Override
        public FList concat(FList last) {
            return last;
        }

        @Override
        public String toString() {
            return "Nil";
        }

    };

}

