package org.jtorrent.client;

/**
 * @author Daniyar Itegulov
 */
@FunctionalInterface
public interface ConsumerE<T, E extends Exception> {
    void accept(T t) throws E;
}
