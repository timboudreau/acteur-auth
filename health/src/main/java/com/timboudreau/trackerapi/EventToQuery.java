package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.util.preconditions.Exceptions;
import com.mongodb.BasicDBObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;
import static com.timboudreau.trackerapi.Properties.*;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 *
 * @author Tim Boudreau
 */
final class EventToQuery implements Provider<BasicDBObject> {

    private final Provider<HttpEvent> provider;

    @Inject
    public EventToQuery(Provider<HttpEvent> provider) {
        this.provider = provider;
    }

    public EventToQuery(HttpEvent evt) {
        this(Providers.of(evt));
    }

    @Override
    public BasicDBObject get() {
        HttpEvent evt = provider.get();
        BasicDBObject obj = new BasicDBObject();
        String[] params = new String[]{added, duration, end, start};

        for (String param : params) {
            boolean found = false;
            for (Patterns p : Patterns.values()) {
                if (p.process(obj, param, evt)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                String v = evt.urlParameter(param);
                System.out.println("GOT PARAMETER " + v);
                if (v != null) {
                    try {
                        v = URLDecoder.decode(v, "UTF-8");
                        System.out.println(" CONVERTED TO " + v);
                    } catch (UnsupportedEncodingException ex) {
                        Exceptions.chuck(ex);
                    }
                    long val = Long.parseLong(v);
                    obj.put(param, val);
                }
            }
        }

        for (Map.Entry<String, String> e : evt.urlParametersAsMap().entrySet()) {
            switch (e.getKey()) {
                case duration:
                case end:
                case start:
                case added:
                case detail:
                case summary:
                case shift:
                case length:
                case moveTo:
                case newStart:
                case newEnd:
                case fields:
                    break;
                default:
                    String v = e.getValue();
                    if (v.indexOf(',') > 0) {
                        String[] spl = v.split(",");
                        List<String> l = new LinkedList<>();
                        for (String s : spl) {
                            l.add(s.trim());
                        }
                        obj.append(e.getKey(), l);
                    } else {
                        obj.append(e.getKey(), v);
                    }
            }
        }
        obj = onQueryConstructed(evt, obj);
        return obj;
    }

    protected BasicDBObject onQueryConstructed(HttpEvent evt, BasicDBObject obj) {
        if (!obj.isEmpty()) {
            obj.put(Properties.type.toString(), Properties.time.toString());
            String idparam = evt.urlParameter(_id);
            if (idparam != null) {
                obj.put(_id, new ObjectId(idparam));
            }
            String uid = evt.urlParameter(by);
            if (uid != null) {
                obj.put("by", new ObjectId(uid));
            }
        }
        return obj;
    }

    private static enum Patterns {

        GTE(">[e=](\\d+)$"),
        LTE("<[e=](\\d+)$"),
        GT(">(\\d+)$"),
        LT("<(\\d+)$");
        private final Pattern pattern;

        Patterns(String pattern) {
            this.pattern = Pattern.compile(pattern);
        }

        boolean process(BasicDBObject ob, String name, HttpEvent evt) {
            String val = evt.urlParameter(name);
            if (val != null) {
                try {
                    val = URLDecoder.decode(val, "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    Exceptions.chuck(ex);
                }
                return decorate(ob, name, val);
            }
            return false;
        }

        private Long get(String val) {
            Matcher m = pattern.matcher(val);
            boolean found = m.find();
            if (found) {
                return Long.parseLong(m.group(1));
            }
            return null;
        }

        boolean decorate(BasicDBObject ob, String name, String val) {
            Long value = get(val);
            if (value != null) {
                ob.put(name, new BasicDBObject(toString(), value));
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return '$' + name().toLowerCase();
        }
    }
}
