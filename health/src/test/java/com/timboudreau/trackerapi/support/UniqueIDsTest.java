package com.timboudreau.trackerapi.support;

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
        UniqueIDs instance = new UniqueIDs("woofler");
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
    
}
