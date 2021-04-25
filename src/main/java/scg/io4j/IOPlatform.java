package scg.io4j;

class IOPlatform {

    static Throwable composeErrors(Throwable head, FList<Throwable> rest) {

        while (rest.nonEmpty()) {
            head.addSuppressed(rest.getHead());
            rest = rest.getTail();
        }

        return head;

    }
}
