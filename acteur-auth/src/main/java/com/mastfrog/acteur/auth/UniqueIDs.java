package com.mastfrog.acteur.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.util.Streams;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.joda.time.DateTimeUtils;

/**
 * A source of unlikely-to-collide, hard-to-guess random url-safe strings,
 * incorporating a system-specific component and the MAC address of the
 * network cards on the system.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class UniqueIDs {

    private final long FIRST = DateTimeUtils.currentTimeMillis();
    private final AtomicLong seq = new AtomicLong(FIRST);
    private final Random random;
    private final String base;
    private final long vmid;

    @Inject
    UniqueIDs(@Named("application") String applicationName) throws SocketException, IOException {
        SecureRandom sr = new SecureRandom();
        random = new Random(sr.nextLong());
        vmid = Math.abs(sr.nextLong());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(longToBytes(vmid));
        byte[] addrBytes = new byte[6];
        for (NetworkInterface i : CollectionUtils.toIterable(NetworkInterface.getNetworkInterfaces())) {
            if (!i.isLoopback() && i.isUp() && !i.isVirtual()) {
                byte[] macAddress = i.getHardwareAddress();
                xor(macAddress, addrBytes);
            }
        }
        baos.write(addrBytes);
        File home = new File(System.getProperty("user.home"));
        File appfile = new File(home, '.' + applicationName);
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

    private void xor(byte[] src, byte[] dest) {
        for (int i = 0; i < Math.min(src.length, dest.length); i++) {
            dest[i] ^= src[i];
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

    private StringBuilder shuffle() {
        StringBuilder sb = new StringBuilder(base);
        int len = sb.length();
        for (int i = 0; i < len; i++) {
            int pos = random.nextInt(len);
            if (pos != i) {
                char hold = sb.charAt(i);
                char other = sb.charAt(pos);
                sb.setCharAt(i, other);
                sb.setCharAt(pos, hold);
            }
        }
        return sb;
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
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return bytesToString(bytes) + '-' + bytesToString(longToBytes(vmid));
    }
}
