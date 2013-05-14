package com.timboudreau.trackerapi.support;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.mastfrog.acteur.auth.UniqueIDs;
import com.mastfrog.giulius.Dependencies;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author tim
 */
public class UniqueIDsTest {
    
    @Test
    public void testNewId() throws SocketException, IOException {
        UniqueIDs instance = new Dependencies(new M()).getInstance(UniqueIDs.class);
        System.out.println("UIDS " + instance);
        int size = 100;
        Set<String> ids = new HashSet<>(size);
        for (int i=0; i < size; i++) {
            String id = instance.newId();
            System.out.println(id);
            ids.add(id);
        }
        assertEquals(size, ids.size());
    }
    
    private static final class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(String.class).annotatedWith(Names.named("application")).toInstance(UniqueIDs.class.getName());
        }
        
    }
}
