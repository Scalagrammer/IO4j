package scg.io4j;

public interface AsyncCallback<R> {
    void success(R        result);
    void failure(Throwable cause);
}
