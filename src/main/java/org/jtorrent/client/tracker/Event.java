package org.jtorrent.client.tracker;

/**
 * @author Daniyar Itegulov
 */
public enum Event {
    STARTED("started"), STOPPED("stopped"), COMPLETED("completed");

    public final String name;

    Event(String name) {
        this.name = name;
    }
}
