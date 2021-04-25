package scg.io4j;

import lombok.val;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@SuppressWarnings("unchecked")
public final class ArrayStack<A> {

    private Object[] items;
    private int      index;
    private int      chunk;

    private final int module;

    public ArrayStack() {
        this(8);
    }

    public ArrayStack(int chunk) {
        this(chunk, 0);
    }

    private ArrayStack(int chunk, int index) {
        //
        this.items = new Object[chunk];
        //
        this.module = (chunk - 1);
        //
        this.chunk = chunk;
        //
        this.index = index;
        //
    }

    public boolean isEmpty() {
        return (index == 0) && isNull(items[0]);
    }

    public void push(A a) {

        if (index == module) {

            val newArray = new Object[chunk];

            newArray[0] = items;

            this.items = newArray;

            this.index = 1;

        } else {
            this.index += 1;
        }

        this.items[index] = a;

    }

    public void pushAll(Iterator<A> cursor) {
        while (cursor.hasNext()) push(cursor.next());
    }

    public void pushAll(Iterable<A> cursor) {
        this.pushAll(cursor.iterator());
    }

    public void pushAll(ArrayStack<A> stack) {
        this.pushAll(stack.reverse());
    }

    @SuppressWarnings("unchecked")
    public A pop() {

        if (index == 0) {
            if (nonNull(items[0])) {
                this.items = (Object[]) items[0];
                this.index = module;
            } else {
                return null;
            }
        }

        A result = (A) items[index];

        this.items[index] = null;

        this.index -= 1;

        return result;

    }

    @SuppressWarnings("unchecked")
    public Iterator<A> reverse() {
        return new Iterator<>() {

            Object[] items = ArrayStack.this.items;

            int index = ArrayStack.this.index;

            @Override
            public boolean hasNext() {
                return index > 0 || nonNull(items[0]);
            }

            @Override
            public A next() {

                if (index == 0) {
                    this.items = (Object[]) items[0];
                    this.index = module;
                }

                if (isNull(items[index])) {
                    throw new NoSuchElementException();
                }

                A result = (A) items[index];

                this.index -= 1;

                return result;

            }
        };
    }
}
