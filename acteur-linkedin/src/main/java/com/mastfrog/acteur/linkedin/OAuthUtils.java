package com.mastfrog.acteur.linkedin;

import static com.mastfrog.acteur.linkedin.OAuthUtils.OAuthHeaders.oauth_consumer_key;
import static com.mastfrog.acteur.linkedin.OAuthUtils.OAuthHeaders.oauth_signature;
import static com.mastfrog.acteur.linkedin.OAuthUtils.OAuthHeaders.oauth_signature_method;
import static com.mastfrog.acteur.linkedin.OAuthUtils.OAuthHeaders.oauth_timestamp;
import static com.mastfrog.acteur.linkedin.OAuthUtils.OAuthHeaders.oauth_token;
import static com.mastfrog.acteur.linkedin.OAuthUtils.OAuthHeaders.oauth_version;
import com.mastfrog.acteur.util.Method;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.joda.time.DateTimeUtils;

/**
 *
 * @author Tim Boudreau
 */
public class OAuthUtils {
    private final String secret;
    private final String apiKey;

    public OAuthUtils(String secret, String apiKey) {
        this.secret = secret;
        this.apiKey = apiKey;
    }
    
    public String encode(String value) throws UnsupportedEncodingException {
        String encoded = null;
        encoded = URLEncoder.encode(value, "UTF-8");
        StringBuilder buf = new StringBuilder(encoded.length());
        char focus;
        for (int i = 0; i < encoded.length(); i++) {
            focus = encoded.charAt(i);
            if (focus == '*') {
                buf.append("%2A");
            } else if (focus == '+') {
                buf.append("%20");
            } else if (focus == '%' && (i + 1) < encoded.length()
                    && encoded.charAt(i + 1) == '7' && encoded.charAt(i + 2) == 'E') {
                buf.append('~');
                i += 2;
            } else {
                buf.append(focus);
            }
        }
        return buf.toString();
    }
    
    public SigBuilder newSignatureBuilder() {
        return new SigBuilder();
    }

    static enum OAuthHeaders {

        oauth_consumer_key(0),
        oauth_nonce(3),
        oauth_signature(6),
        oauth_signature_method(1),
        oauth_timestamp(2),
        oauth_token(5),
        oauth_version(4);
        private final int order;

        OAuthHeaders(int order) {
            // After hours of testing, the *only* difference between a failing
            // and succeeding query was the order of parameters - signatures
            // and *everything* else was identical.  So this parameter order is
            // magical
            this.order = order;
        }

        static int orderOf(String s) {
            for (OAuthHeaders a : values()) {
                if (a.toString().equals(s)) {
                    return a.order;
                }
            }
            return Integer.MAX_VALUE;
        }

        public static List<String> sortKeys(Map<String, ?> m) {
            List<String> result = new ArrayList<>(m.keySet());
            Collections.sort(result, new MagicOrderComparator());
            return result;
        }

        private static final class MagicOrderComparator implements Comparator<String> {

            @Override
            public int compare(String o1, String o2) {
                Integer oa = orderOf(o1);
                Integer ob = orderOf(o2);
                return oa.compareTo(ob);
            }

        }

    }

    final class SigBuilder {

        private final Map<String, String> pairs = new HashMap<>();

        SigBuilder() {
            add(oauth_version, "1.0")
                    .add(oauth_timestamp, "" + (DateTimeUtils.currentTimeMillis() / 1000))
                    .add(oauth_consumer_key, apiKey)
                    .add(oauth_signature_method, "HMAC-SHA1");
        }

        public SigBuilder setToken(String token) {
            return add(oauth_token, token);
        }

        public SigBuilder add(String key, String val) {
            pairs.put(key, val);
            return this;
        }

        public SigBuilder add(OAuthHeaders hdr, String value) {
            pairs.put(hdr.toString(), value);
            return this;
        }

        private List<String> sortedKeys(Map<String, String> pairs) {
            List<String> result = new ArrayList<>(pairs.keySet());
            Collections.sort(result);
            return result;
        }

        public String buildSignature(Method mth, String endpoint, AuthorizationResponse auth) throws UnsupportedEncodingException, GeneralSecurityException {
            endpoint = URLDecoder.decode(endpoint, "UTF-8");
            endpoint = encode(endpoint).replace("%5f", "_");
            StringBuilder sb = new StringBuilder(mth.name()).append('&').append(endpoint).append('&');
            StringBuilder content = new StringBuilder();
            boolean first = true;
            for (String key : sortedKeys(pairs)) {
                if (!first) {
                    // appended above so it will not be encoded as %26 - the
                    // rest of the &'s should be - go figure.
                    content.append('&');
                } else {
                    first = false;
                }
                content.append(key).append('=').append(pairs.get(key));
            }
            sb.append(encode(content.toString()));
            System.out.println("SIGSTRING: " + sb);
            String oauth_signature = generateSignature(sb.toString(), auth);
            System.out.println("SIGNATURE: " + oauth_signature);
            return oauth_signature;
        }

        public String toHeader(Method mth, String endpoint, AuthorizationResponse auth) throws UnsupportedEncodingException, GeneralSecurityException {
            String sig = buildSignature(mth, endpoint, auth);
            Map<String, String> m = new HashMap<>(pairs);
            m.put(oauth_signature.toString(), encode(sig));
            StringBuilder result = new StringBuilder();
            for (String key : OAuthHeaders.sortKeys(m)) {
                String val = m.get(key);
                if (result.length() != 0) {
                    result.append(",");
                }
                result.append(key).append('=').append('"').append(val).append('"');
            }
            result.insert(0, "OAuth ");

            return result.toString();
        }
    }
    private static final String ALGORITHM = "HmacSHA1";

    String generateSignature(String data, AuthorizationResponse token) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        byte[] byteHMAC = null;
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec spec;
        if (token == null) {
            String signature = encode(secret) + "&";
            spec = new SecretKeySpec(signature.getBytes(), ALGORITHM);
        } else {
            String signature = encode(secret)
                    + "&" + encode(token.accessTokenSecret);
            spec = new SecretKeySpec(signature.getBytes(), ALGORITHM);
        }
        mac.init(spec);
        byteHMAC = mac.doFinal(data.getBytes());
        String sig = encode(byteHMAC);
        System.out.println("TSIGSTRING: " + data);
        System.out.println("TSIGNATURE: " + sig);
        return sig;
    }

    private static final char last2byte = (char) Integer.parseInt("00000011", 2);
    private static final char last4byte = (char) Integer.parseInt("00001111", 2);
    private static final char last6byte = (char) Integer.parseInt("00111111", 2);
    private static final char lead6byte = (char) Integer.parseInt("11111100", 2);
    private static final char lead4byte = (char) Integer.parseInt("11110000", 2);
    private static final char lead2byte = (char) Integer.parseInt("11000000", 2);
    private static final char[] encodeTable = new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'};

    public static String encode(byte[] from) {
        StringBuilder to = new StringBuilder((int) (from.length * 1.34) + 3);
        int num = 0;
        char currentByte = 0;
        for (int i = 0; i < from.length; i++) {
            num = num % 8;
            while (num < 8) {
                switch (num) {
                    case 0:
                        currentByte = (char) (from[i] & lead6byte);
                        currentByte = (char) (currentByte >>> 2);
                        break;
                    case 2:
                        currentByte = (char) (from[i] & last6byte);
                        break;
                    case 4:
                        currentByte = (char) (from[i] & last4byte);
                        currentByte = (char) (currentByte << 2);
                        if ((i + 1) < from.length) {
                            currentByte |= (from[i + 1] & lead2byte) >>> 6;
                        }
                        break;
                    case 6:
                        currentByte = (char) (from[i] & last2byte);
                        currentByte = (char) (currentByte << 4);
                        if ((i + 1) < from.length) {
                            currentByte |= (from[i + 1] & lead4byte) >>> 4;
                        }
                        break;
                }
                to.append(encodeTable[currentByte]);
                num += 6;
            }
        }
        if (to.length() % 4 != 0) {
            for (int i = 4 - to.length() % 4; i > 0; i--) {
                to.append("=");
            }
        }
        return to.toString();
    }
}
