package com.mastfrog.acteur.auth;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A source of unlikely-to-collide, hard-to-guess random url-safe strings,
 * incorporating a system-specific component and the MAC address of the network
 * cards on the system.
 *
 * @author Tim Boudreau
 * @deprecated Moved to com.mastfrog.util
 */
@Singleton
@Deprecated
public final class UniqueIDs {

    private final long FIRST = System.currentTimeMillis();
    private final AtomicLong seq = new AtomicLong(FIRST);
    private final Random random;
    private final String base;
    private final long vmid;

    public UniqueIDs(File appfile) throws IOException {
        SecureRandom sr = new SecureRandom();
        random = new Random(sr.nextLong());
        vmid = Math.abs(sr.nextLong());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(longToBytes(vmid));
        byte[] addrBytes = new byte[6];
        for (NetworkInterface i : CollectionUtils.toIterable(NetworkInterface.getNetworkInterfaces())) {
            if (!i.isLoopback() && i.isUp() && !i.isVirtual()) {
                byte[] macAddress = i.getHardwareAddress();
                if (macAddress != null) {
                    xor(macAddress, addrBytes);
                }
            }
        }
        baos.write(addrBytes);
        if (appfile.exists()) {
            try (FileInputStream in = new FileInputStream(appfile)) {
                Streams.copy(in, baos, 8);
            }
        } else {
            byte[] bts = new byte[8];
            random.nextBytes(bts);
            appfile.createNewFile();
            try (FileOutputStream out = new FileOutputStream(appfile)) {
                out.write(bts);
            }
            baos.write(bts);
        }
        base = bytesToString(baos.toByteArray());
    }

    public static final class UniqueIdsModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(UniqueIDs.class).toProvider(IdsProvider.class).in(Scopes.SINGLETON);
        }

        static class IdsProvider implements Provider<UniqueIDs> {

            private final Provider<String> name;
            private UniqueIDs ids;

            @Inject
            IdsProvider(@Named("application") Provider<String> applicationName) {
                this.name = applicationName;
            }

            @Override
            public UniqueIDs get() {
                if (ids == null) {
                    File home = new File(System.getProperty("user.home"));
                    File appfile = new File(home, '.' + name.get());
                    try {
                        ids = new UniqueIDs(appfile);
                    } catch (IOException ex) {
                        throw new ConfigurationError(ex);
                    }
                }
                return ids;
            }

        }
    }

    private void xor(byte[] src, byte[] dest) {
        if (src != null && dest != null) {
            for (int i = 0; i < Math.min(src.length, dest.length); i++) {
                dest[i] ^= src[i];
            }
        }
    }

    private String bytesToString(byte[] b) {
        LongBuffer lb = ByteBuffer.wrap(b).asLongBuffer();
        StringBuilder sb = new StringBuilder();
        while (lb.position() < lb.capacity()) {
            long val = Math.abs(lb.get());
            sb.append(Long.toString(val, 36));
        }
        return sb.toString();
    }

    private byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(x);
        return buffer.array();
    }

    public String newId() {
        int inc = random.nextInt(13) + 1;
        long ix = seq.getAndAdd(inc);
        int val = random.nextInt(Integer.MAX_VALUE);
        return new StringBuilder(base).append(Integer.toString(val, 36))
                .append(Long.toString(ix, 36)).toString();
    }

    @Override
    public String toString() {
        return base;
    }

    public String newRandomString() {
        return newRandomString(16);
    }

    public String newRandomString(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytesToString(bytes) + '-' + bytesToString(longToBytes(vmid));
    }
}
