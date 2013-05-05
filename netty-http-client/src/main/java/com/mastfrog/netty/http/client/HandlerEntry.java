package com.mastfrog.netty.http.client;

import com.mastfrog.util.thread.Receiver;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
final class HandlerEntry<T> {

    final Class<? extends State<T>> state;
    private final List<Receiver<T>> receivers = new LinkedList<>();

    HandlerEntry(Class<? extends State<T>> state) {
        this.state = state;
    }

    void add(Receiver<T> r) {
        receivers.add(r);
    }

    void onEvent(State<T> state) {
        for (Receiver<T> r : receivers) {
            r.receive(state.get());
        }
    }
}
