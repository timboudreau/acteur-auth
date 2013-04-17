package com.timboudreau.trackerapi.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.joda.time.MutableInterval;

/**
 *
 * @author Tim Boudreau
 */
public final class Intervals implements Iterable<Interval> {

    private List<MI> value = new ArrayList<>();

    private List<MI> winnow() {
        List<MI> result = new ArrayList<>();
        MI prev = null;
        for (MI i : value) {
            i = i.dup();
            if (prev != null) {
                if (!merge(prev, i.toInterval(), null)) {
                    result.add(i);
                } else {
                    prev.ids.addAll(i.ids);
                }
            } else {
                result.add(i);
            }
            prev = i;
        }
        return result;
    }

    public String toJSON(boolean ivals, boolean summary) throws IOException {
        List<Stub> stubs = new LinkedList<>();
        long duration = 0;
        long start = Long.MAX_VALUE;
        long end = Long.MIN_VALUE;
        List<String> allIds = new ArrayList<>();
        for (MI i : winnow()) {
            duration += i.getEndMillis() - i.getStartMillis();
            start = Math.min(i.getStartMillis(), start);
            end = Math.max(end, i.getEndMillis());
            stubs.add(new Stub(i));
            allIds.addAll(i.ids);
        }
        ObjectMapper m = new ObjectMapper();
        Map<String, Object> map = new HashMap<>();
        if (start == Long.MAX_VALUE) {
            start = 0;
            end = 0;
        }
        if (summary) {
            Stub all = new Stub(start, end);
            all.ids.addAll(allIds);
            map.put("period", all);
        }
        map.put("total", duration);
        if (ivals) {
            map.put("intervals", stubs);
        }
        return m.writeValueAsString(map) + '\n';
    }

    private boolean merge(MI i, Interval ival, String id) {
        if (ival.overlaps(i)) {
            DateTime sa = i.getStart();
            DateTime ea = i.getEnd();
            DateTime se = ival.getStart();
            DateTime ne = ival.getEnd();
            DateTime a = sa.isBefore(se) ? sa : se;
            DateTime b = ea.isAfter(ne) ? ea : ne;
            i.setStart(a);
            i.setEnd(b);
            i.ids.add(id);
            return true;
        }
        return false;
    }

    public void add(Interval ival, String id) {
        for (MI i : value) {
            if (merge(i, ival, id)) {
                return;
            }
        }
        value.add(new MI(ival.toMutableInterval(), id));
        Collections.sort(value, new C());
    }

    public Duration total() {
        long result = 0;
        for (MI i : value) {
            result += i.toDurationMillis();
        }
        return new Duration(result);
    }

    @Override
    public Iterator<Interval> iterator() {
        return new WrapI(winnow().iterator());
    }

    static class MI extends MutableInterval {

        Set<String> ids = new HashSet<>();

        MI(MutableInterval m, String id) {
            super(m);
            if (id != null) {
                ids.add(id);
            }
        }

        MI dup() {
            MI nue = new MI(this, null);
            nue.ids.addAll(ids);
            return nue;
        }
    }

    static class WrapI<T extends Interval> implements Iterator<Interval> {

        private final Iterator<T> real;

        public WrapI(Iterator<T> real) {
            this.real = real;
        }

        @Override
        public boolean hasNext() {
            return real.hasNext();
        }

        @Override
        public Interval next() {
            return real.next().toInterval();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    static final class C<Object> implements Comparator<Object> {

        private Interval toInterval(Object o) {
            if (o instanceof Interval) {
                return (Interval) o;
            }
            if (o instanceof MutableInterval) {
                return ((MutableInterval) o).toInterval();
            }
            return (Interval) o; //will fail
        }

        @Override
        public int compare(Object t, Object t1) {
            return toInterval(t).getStart().compareTo(toInterval(t1).getStart());
        }
    }

    public static class Stub {

        public final long start;
        public final long end;
        public final long dur;
        public final List<String> ids = new ArrayList<>();

        public Stub(MI iv) {
            this(iv.getStartMillis(), iv.getEndMillis());
            ids.addAll(iv.ids);
            Collections.sort(ids);
        }

        public Stub(long start, long end) {
            this.start = start;
            this.end = end;
            this.dur = end - start;
        }
    }
}
