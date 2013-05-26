/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mastfrog.acteur.twitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.mastfrog.acteur.auth.OAuthPlugin.RemoteUserInfo;
import static com.mastfrog.acteur.twitter.TwitterSign.OAuthHeaders.oauth_consumer_key;
import static com.mastfrog.acteur.twitter.TwitterSign.OAuthHeaders.oauth_signature;
import static com.mastfrog.acteur.twitter.TwitterSign.OAuthHeaders.oauth_signature_method;
import static com.mastfrog.acteur.twitter.TwitterSign.OAuthHeaders.oauth_timestamp;
import static com.mastfrog.acteur.twitter.TwitterSign.OAuthHeaders.oauth_token;
import static com.mastfrog.acteur.twitter.TwitterSign.OAuthHeaders.oauth_version;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.client.ResponseHandler;
import com.mastfrog.netty.http.client.StateType;
import com.mastfrog.url.Host;
import com.mastfrog.url.Protocols;
import com.mastfrog.url.URL;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.thread.Receiver;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTimeUtils;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.internal.http.BASE64Encoder;
import twitter4j.internal.http.HttpParameter;
import twitter4j.internal.http.RequestMethod;

/**
 * *
 * ______ _____ ___ ______ _____ _ _ _____ _____ | ___ \ ___|/ _ \| _ \ |_ _| |
 * | |_ _/ ___| | |_/ / |__ / /_\ \ | | | | | | |_| | | | \ `--. | /| __|| _ | |
 * | | | | | _ | | | `--. \ | |\ \| |___| | | | |/ / | | | | | |_| |_/\__/ / \_|
 * \_\____/\_| |_/___/ \_/ \_| |_/\___/\____/
 * ---------------------------------------------------------------------- While
 * you're struggling to get this working, I highly recommend three things:
 *
 * 1. First, use HTTP, not HTTPS so you can see what you're doing, then switch
 * back to HTTPS once it's working 2. Use Fiddler or Wireshark to see your
 * actual requests and the Twitter responses 3. Use the example data from the
 * following address. Get that working first as a baseline, then use your own
 * credentials: https://dev.twitter.com/docs/auth/implementing-sign-twitter
 *
 *
 * // REQUIRED LIBRARIES // Apache commons codec // Apache HTTP Core // JSON
 *
 */
// Taken from here - license unknown: https://github.com/cyrus7580/twitter_api_examples
// once you've got the generic request token, send the user to the authorization page. They grant access and either
// a) are shown a pin number
// b) sent to the callback url with information
// In either case, turn the authorization code into a twitter access token for that user.
// My example here is uses a pin and oauth_token (from the previous request token call)
// INPUT: pin, generic request token
// OUTPUT: if successful, twitter API will return access_token, access_token_secret, screen_name and user_id
public class TwitterSign {

    private final String callbackUrl;
    private final String twitter_consumer_key;
    private final String twitter_consumer_secret;
    private final HttpClient client;

    TwitterSign(String twitter_consumer_key, String twitter_consumer_secret, String callbackUrl, HttpClient client) {
        this.twitter_consumer_key = twitter_consumer_key;
        this.twitter_consumer_secret = twitter_consumer_secret;
        this.callbackUrl = callbackUrl;
        this.client = client;
    }

    private static final class ResponseLatch extends Receiver<Void> {

        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void receive(Void object) {
            latch.countDown();
        }
    }

    private static class RH extends ResponseHandler<String> {

        private volatile String result;
        private volatile Throwable throwable;
        private volatile String error;
        private volatile HttpHeaders headers;
        private volatile HttpResponseStatus status;

        RH() {
            super(String.class);
        }

        @Override
        protected void receive(HttpResponseStatus status, HttpHeaders headers, String result) {
            this.result = result;
            this.headers = headers;
            this.status = status;
        }

        @Override
        protected void onErrorResponse(HttpResponseStatus status, HttpHeaders headers, String content) {
            this.status = status;
            this.headers = headers;
            this.error = content;
        }

        @Override
        protected void onError(Throwable err) {
            this.throwable = err;
        }

        public String getResponse() throws IOException {
            if (throwable != null) {
                Exceptions.chuck(throwable);
            }
            if (error != null) {
                throw new IOException(toString());
            }
            return result;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                    .append('@').append(System.identityHashCode(this)).append(" ");
            if (status != null) {
                sb.append(status).append("\n");
            }
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entries()) {
                    sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                }
            }
            if (result != null) {
                sb.append(result).append("\n");
            }
            if (error != null) {
                sb.append(result).append("\n");
            }
            return sb.toString();
        }
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

    private static String computeSignature(String baseString, String keyString) throws GeneralSecurityException, UnsupportedEncodingException {
        SecretKey secretKey = null;

        byte[] keyBytes = keyString.getBytes();
        secretKey = new SecretKeySpec(keyBytes, "HmacSHA1");

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(secretKey);

        byte[] text = baseString.getBytes();

        return new String(Base64.encodeBase64(mac.doFinal(text))).trim();
    }

    // the first step in the twitter oauth flow is to get a request token with a call to api.twitter.com/oauth/request_token
    // INPUT: nothing
    // OUTPUT: if successful, twitter API will return oauth_token, oauth_token_secret and oauth_token_confirmed
    public OAuthResult startTwitterAuthentication(String oauth_nonce) throws IOException, InterruptedException, GeneralSecurityException {

        // this particular request uses POST
        String get_or_post = "POST";

        // I think this is the signature method used for all Twitter API calls
        String oauth_signature_method = "HMAC-SHA1";

        // get the timestamp
        long ts = DateTimeUtils.currentTimeMillis();
        String oauth_timestamp = (new Long(ts / 1000)).toString(); // then divide by 1000 to get seconds

        // assemble the proper parameter string, which must be in alphabetical order, using your consumer key
        String parameter_string = "oauth_consumer_key="
                + twitter_consumer_key + "&oauth_nonce=" + oauth_nonce
                + "&oauth_signature_method=" + oauth_signature_method
                + "&oauth_timestamp=" + oauth_timestamp + "&oauth_version=1.0";
        System.out.println("parameter_string=" + parameter_string); // print out parameter string for error checking, if you want

        // specify the proper twitter API endpoint at which to direct this request
        String twitter_endpoint = "https://api.twitter.com/oauth/request_token";
        String twitter_endpoint_host = "api.twitter.com";
        String twitter_endpoint_path = "/oauth/request_token";

        // assemble the string to be signed. It is METHOD & percent-encoded endpoint & percent-encoded parameter string
        // Java's native URLEncoder.encode function will not work. It is the wrong RFC specification (which does "+" where "%20" should be)...
        // the encode() function included in this class compensates to conform to RFC 3986 (which twitter requires)
        String signature_base_string = get_or_post + "&" + encode(twitter_endpoint) + "&" + encode(parameter_string);

        // now that we've got the string we want to sign (see directly above) HmacSHA1 hash it against the consumer secret
        String oauth_signature = "";
        oauth_signature = computeSignature(signature_base_string, twitter_consumer_secret + "&");  // note the & at the end. Normally the user access_token would go here, but we don't know it yet for request_token

        // each request to the twitter API 1.1 requires an "Authorization: BLAH" header. The following is what BLAH should look like
        String authorization_header_string = "OAuth oauth_consumer_key=\"" + twitter_consumer_key + "\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\""
                + oauth_timestamp + "\",oauth_nonce=\"" + oauth_nonce + "\",oauth_version=\"1.0\",oauth_signature=\"" + encode(oauth_signature) + "\"";
        System.out.println("authorization_header_string=" + authorization_header_string); 	// print out authorization_header_string for error checking

        String oauth_token = "";
        String oauth_token_secret = "";
        String oauth_callback_confirmed = "";

        // initialize the HTTPS connection
        // for HTTP, use this instead of the above.
        // Socket socket = new Socket(host.getHostName(), host.getPort());
        // conn.bind(socket, params);
        ResponseLatch receiver = new ResponseLatch();
        RH rh = new RH();

        client.post()
                .setBody("", MediaType.parse("application/x-www-form-urlencoded").withCharset(CharsetUtil.UTF_8))
                .addHeader(Headers.stringHeader("Authorization"), authorization_header_string)
                .setURL(URL.builder().setProtocol(Protocols.HTTPS).setHost(Host.parse(twitter_endpoint_host)).setPath(twitter_endpoint_path).create().toString())
                .on(StateType.Closed, receiver).execute(rh);

        receiver.latch.await(1, TimeUnit.MINUTES);
        String responseBody = rh.getResponse();
        StringTokenizer st = new StringTokenizer(responseBody, "&");
        String currenttoken = "";
        while (st.hasMoreTokens()) {
            currenttoken = st.nextToken();
            if (currenttoken.startsWith("oauth_token=")) {
                oauth_token = currenttoken.substring(currenttoken.indexOf("=") + 1);
            } else if (currenttoken.startsWith("oauth_token_secret=")) {
                oauth_token_secret = currenttoken.substring(currenttoken.indexOf("=") + 1);
            } else if (currenttoken.startsWith("oauth_callback_confirmed=")) {
                oauth_callback_confirmed = currenttoken.substring(currenttoken.indexOf("=") + 1);
            } else {
                System.out.println("Warning... twitter returned a key we weren't looking for: " + currenttoken);
            }
        }
        return new OAuthResult(oauth_token, oauth_token_secret, oauth_callback_confirmed);
    }

    public AuthorizationResponse getTwitterAccessTokenFromAuthorizationCode(String pin, String oauth_token, String oauth_nonce) throws IOException, InterruptedException, GeneralSecurityException {

        // this particular request uses POST
        String get_or_post = "POST";

        // I think this is the signature method used for all Twitter API calls
        String oauth_signature_method = "HMAC-SHA1";

        // get the timestamp
        Calendar tempcal = Calendar.getInstance();
        long ts = tempcal.getTimeInMillis();// get current time in milliseconds
        String oauth_timestamp = (new Long(ts / 1000)).toString(); // then divide by 1000 to get seconds

        // the parameter string must be in alphabetical order
        String parameter_string = "oauth_consumer_key=" + twitter_consumer_key + "&oauth_nonce=" + oauth_nonce + "&oauth_signature_method=" + oauth_signature_method
                + "&oauth_timestamp=" + oauth_timestamp + "&oauth_token=" + encode(oauth_token) + "&oauth_version=1.0";
        System.out.println("parameter_string=" + parameter_string);

        String twitter_endpoint = "https://api.twitter.com/oauth/access_token";
        String twitter_endpoint_host = "api.twitter.com";
        String twitter_endpoint_path = "/oauth/access_token";
        String signature_base_string = get_or_post + "&" + encode(twitter_endpoint) + "&" + encode(parameter_string);

        String oauth_signature = computeSignature(signature_base_string, twitter_consumer_secret + "&");  // note the & at the end. Normally the user access_token would go here, but we don't know it yet
        System.out.println("oauth_signature=" + encode(oauth_signature));

        String authorization_header_string = "OAuth oauth_consumer_key=\"" + twitter_consumer_key + "\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"" + oauth_timestamp
                + "\",oauth_nonce=\"" + oauth_nonce + "\",oauth_version=\"1.0\",oauth_signature=\"" + encode(oauth_signature) + "\",oauth_token=\"" + encode(oauth_token) + "\"";
        // System.out.println("authorization_header_string=" + authorization_header_string);

        String access_token = "";
        String access_token_secret = "";
        String user_id = "";
        String screen_name = "";

        RH rh = new RH();
        ResponseLatch latch = new ResponseLatch();

        URL url = URL.builder(Protocols.HTTPS)
                .setHost(Host.parse(twitter_endpoint_host))
                .setPath(twitter_endpoint_path).create();

        client.post().setURL(url).addHeader(Headers.stringHeader("Authorization"), authorization_header_string)
                .setBody("oauth_verifier=" + encode(pin), MediaType.parse("application/x-www-form-urlencoded").withCharset(CharsetUtil.UTF_8))
                .on(StateType.Closed, latch)
                .execute(rh);
        latch.latch.await(1, TimeUnit.MINUTES);

        String responseBody = rh.getResponse();

        StringTokenizer st = new StringTokenizer(responseBody, "&");
        String currenttoken = "";
        while (st.hasMoreTokens()) {
            currenttoken = st.nextToken();
            if (currenttoken.startsWith("oauth_token=")) {
                access_token = currenttoken.substring(currenttoken.indexOf("=") + 1);
            } else if (currenttoken.startsWith("oauth_token_secret=")) {
                access_token_secret = currenttoken.substring(currenttoken.indexOf("=") + 1);
            } else if (currenttoken.startsWith("user_id=")) {
                user_id = currenttoken.substring(currenttoken.indexOf("=") + 1);
            } else if (currenttoken.startsWith("screen_name=")) {
                screen_name = currenttoken.substring(currenttoken.indexOf("=") + 1);
            } else {
                System.out.println("Warning... twitter returned a key we weren't looking for: " + currenttoken);
                // something else. The 4 values above are the only ones twitter should return, so this case would be weird.
                // skip
            }
        }
        if ("".equals(access_token) || "".equals(access_token_secret)) {
            throw new IOException("Missing information in " + responseBody);
        }
        return new AuthorizationResponse(access_token, access_token_secret);
    }

    public String getAuthorizationHeader(Method method, String twitter_endpoint, String oauth_token, String oauth_nonce) throws UnsupportedEncodingException, GeneralSecurityException {
        // this particular request uses POST
        String get_or_post = method.toString();

        // I think this is the signature method used for all Twitter API calls
        String oauth_signature_method = "HMAC-SHA1";

        // get the timestamp
        Calendar tempcal = Calendar.getInstance();
        long ts = tempcal.getTimeInMillis();// get current time in milliseconds
        String oauth_timestamp = (new Long(ts / 1000)).toString(); // then divide by 1000 to get seconds

        // the parameter string must be in alphabetical order
        String parameter_string = "oauth_consumer_key=" + twitter_consumer_key
                + "&oauth_nonce=" + oauth_nonce + "&oauth_signature_method="
                + oauth_signature_method
                + "&oauth_timestamp=" + oauth_timestamp + "&oauth_token="
                + encode(oauth_token) + "&oauth_version=1.0";

        System.out.println("parameter_string=" + parameter_string);

        String signature_base_string = get_or_post + "&" + twitter_endpoint + "&" + encode(parameter_string);

        System.out.println("SIGNATURE BASE: " + signature_base_string);

//        String oauth_signature  = computeSignature(signature_base_string, twitter_consumer_secret + "&");  // note the & at the end. Normally the user access_token would go here, but we don't know it yet
        String oauth_signature = computeSignature(signature_base_string, twitter_consumer_secret + "&");  // note the & at the end. Normally the user access_token would go here, but we don't know it yet
        System.out.println("oauth_signature=" + encode(oauth_signature));

        /*
         OAuth oauth_consumer_key="cI5QfItOsHq08gRBWGFzmg",
         oauth_nonce="621132bc40c29af5a519f0ba9b0c7ffa",
         oauth_signature="dupbxKL9Zqu22a5NF3wxME2fPTE%3D",
         oauth_signature_method="HMAC-SHA1",
         oauth_timestamp="1369522015",
         oauth_token="260043244-SluDCyRqH0SzIjCXpRTbvkSiNCDZhjuwXmaNiB1h",
         oauth_version="1.0"
         */
        String authorization_header_string = "OAuth oauth_consumer_key=\""
                + twitter_consumer_key + "\", "
                + "oauth_nonce=\"" + oauth_nonce + "\", "
                + "oauth_signature=\"" + encode(oauth_signature) + "\", "
                + "oauth_signature_method=\"HMAC-SHA1\", "
                + "oauth_timestamp=\"" + oauth_timestamp + "\", "
                + "oauth_token=\"" + encode(oauth_token) + "\", "
                + "oauth_version=\"1.0" + "\"";

        return authorization_header_string;
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
        
        public static List<String> sortKeys(Map<String,?> m) {
            List<String> result = new ArrayList<String>(m.keySet());
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

    private class SigBuilder {

        private final Map<String, String> pairs = new HashMap<>();

        SigBuilder() {
            add(oauth_version, "1.0")
                    .add(oauth_timestamp, "" + (DateTimeUtils.currentTimeMillis() / 1000))
                    .add(oauth_consumer_key, twitter_consumer_key)
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

    RemoteUserInfo zgetUserInfo(String oauth_nonce, TwitterOAuthPlugin.TwitterToken credential, AuthorizationResponse auth) throws UnsupportedEncodingException, GeneralSecurityException, InterruptedException, IOException {
        System.setProperty("twitter4j.http.useSSL", "false");
        
        Twitter twitter = TwitterFactory.getSingleton();
        try {
            // Idiotic - shutdown does not clear state
            twitter.setOAuthConsumer(twitter_consumer_key, twitter_consumer_secret);
        } catch (Exception e) {
            e.printStackTrace();
        }
        twitter.setOAuthAccessToken(new AccessToken(auth.accessToken, auth.accessTokenSecret));
        OAuthAuthorization a = new OAuthAuthorization(twitter.getConfiguration());
        a.setOAuthConsumer(twitter_consumer_key, twitter_consumer_secret);

        URL url = URL.builder(Protocols.HTTPS)
//        URL url = URL.builder(Protocols.HTTP) // XXX NOT UNENCRYPTED!  JUST FOR DEBUGING
                .setHost(Host.parse("api.twitter.com"))
                .setPath("1.1/account/verify_credentials.json").create();

        String hdr = new SigBuilder()
                .add(oauth_token, auth.accessToken)
                .add(OAuthHeaders.oauth_nonce, oauth_nonce)
                .toHeader(Method.GET, url.getPathAndQuery(), auth); // XXX encode URL?

        String franken = a.xgenerateAuthorizationHeader(oauth_nonce, "GET", "/1.1/account/verify_credentials.json", new HttpParameter[0], new twitter4j.auth.AccessToken(auth.accessToken, auth.accessTokenSecret));

        System.out.println("OAUTH PARAMS FROM TW: " + franken);
        System.out.println("HEADER1:" + hdr);

        OAuthAuthorization oa = new OAuthAuthorization(twitter.getConfiguration());
        oa.setOAuthConsumer(twitter_consumer_key, twitter_consumer_secret);
        oa.setOAuthAccessToken(new AccessToken(auth.accessToken, auth.accessTokenSecret));

        twitter4j.internal.http.HttpRequest r = new twitter4j.internal.http.HttpRequest(RequestMethod.GET,
                "https://api.twitter.com/twitter4j.internal.http.HttpRequest",
                new HttpParameter[0], oa, Collections.<String, String>emptyMap());

        System.out.println("AUTH HEADER FROM TW4: " + a.getAuthorizationHeader(r));

        ResponseLatch latch = new ResponseLatch();
        RH rh = new RH();
        client.get().setURL(url)
                .addHeader(Headers.stringHeader("Authorization"), hdr)
                .addHeader(Headers.stringHeader("X-Twitter-Client-URL"), "http://twitter4j.org/en/twitter4j-3.0.4-SNAPSHOT.xml")
                .addHeader(Headers.stringHeader("X-Twitter-Client"), "Twitter4J")
                .addHeader(Headers.stringHeader("Accept-Encoding"), "gzip")
                .addHeader(Headers.stringHeader("X-Twitter-Client-Version"), "3.0.4-SNAPSHOT")
                .addHeader(Headers.stringHeader("Accept"), "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2")
//                .addHeader(Headers.stringHeader("Accept"), "*/*")
//                .addHeader(Headers.stringHeader("Connection"), "keep-alive")
                .noDateHeader()
//                .noConnectionHeader()
//                .addHeader(Headers.stringHeader("Content-Type"), "application/x-www-form-urlencoded")
                //                .setBody("screen_name=kablosna", MediaType.PLAIN_TEXT_UTF_8)
                .on(StateType.Closed, latch)
                .execute(rh);
        rh.await(1, TimeUnit.MINUTES);
        latch.latch.await(1, TimeUnit.MINUTES);

        String responseBody = rh.getResponse();

        System.out.println("RESPONSE BODY IS: " + responseBody);

        RUI rui = new RUI();
        if (responseBody == null) {
//            System.out.println("NULL RESPONSE BODY.");
            throw new IOException(rh.toString());
        }
        rui.putAll(new ObjectMapper().readValue(responseBody, Map.class));
        return rui;
    }

    RemoteUserInfo getUserInfo(String oauth_nonce, TwitterOAuthPlugin.TwitterToken credential, AuthorizationResponse auth) throws UnsupportedEncodingException, GeneralSecurityException, InterruptedException, IOException {
//        System.setProperty("twitter4j.loggerFactory", "twitter4j.internal.logging.StdOutLogger");
        System.setProperty("twitter4j.debug", "true");
        System.setProperty("twitter4j.http.useSSL", "false");

        Twitter twitter = TwitterFactory.getSingleton();
        try {
            // Idiotic 
            twitter.setOAuthConsumer(twitter_consumer_key, twitter_consumer_secret);
        } catch (Exception e) {
            e.printStackTrace();
        }
        twitter.setOAuthAccessToken(new AccessToken(auth.accessToken, auth.accessTokenSecret));
        try {
            User user = twitter.verifyCredentials();
            System.out.println("USER: " + user);

            RUI rui = new RUI();
            rui.put("displayName", user.getName());
            rui.put("name", user.getScreenName() + "@api.twitter.com");
            rui.put("screen_name", user.getScreenName());

            rui.put("picture", user.getProfileImageURLHttps());
            rui.put("pictureLarge", user.getBiggerProfileImageURLHttps());
            rui.put("id", user.getId());
            return rui;
        } catch (TwitterException ex) {
            throw new IOException(ex);
        } finally {
            twitter.shutdown();
        }
    }

    RemoteUserInfo ygetUserInfo(String oauth_nonce, TwitterOAuthPlugin.TwitterToken credential, AuthorizationResponse auth) throws UnsupportedEncodingException, GeneralSecurityException, InterruptedException, IOException {
        URL url = URL.builder(Protocols.HTTPS)
                .setHost(Host.parse("api.twitter.com"))
                .setPath("1.1/account/verify_credentials.json").create();

        String hdr = new SigBuilder()
                .add(oauth_token, auth.accessToken)
                .add(OAuthHeaders.oauth_nonce, oauth_nonce)
                .toHeader(Method.GET, url.toString(), auth); // XXX encode URL?

        ResponseLatch latch = new ResponseLatch();
        RH rh = new RH();
        client.get().setURL(url).addHeader(Headers.stringHeader("Authorization"), hdr)
                .addHeader(Headers.stringHeader("Accept"), "*/*")
                .addHeader(Headers.stringHeader("Content-Type"), "application/x-www-form-urlencoded")
                //                .setBody("screen_name=kablosna", MediaType.PLAIN_TEXT_UTF_8)
                .on(StateType.Closed, latch)
                .execute(rh);
        rh.await(1, TimeUnit.MINUTES);
        latch.latch.await(1, TimeUnit.MINUTES);

        String responseBody = rh.getResponse();

        System.out.println("RESPONSE BODY IS: " + responseBody);

        RUI rui = new RUI();
        if (responseBody == null) {
//            System.out.println("NULL RESPONSE BODY.");
            throw new IOException(rh.toString());
        }
        rui.putAll(new ObjectMapper().readValue(responseBody, Map.class));
        return rui;
    }

    RemoteUserInfo xgetUserInfo(String oauth_nonce, TwitterOAuthPlugin.TwitterToken credential, AuthorizationResponse auth) throws UnsupportedEncodingException, GeneralSecurityException, InterruptedException, IOException {

        URL url = URL.builder(Protocols.HTTPS)
                .setHost(Host.parse("api.twitter.com"))
                .setPath("1.1/account/verify_credentials.json").create();

        String authorization_header_string = getAuthorizationHeader(Method.GET,
                "https%3A%2F%2Fapi.twitter.com%2F1.1%2Faccount%2Fverify_credentials.json", auth.accessToken, oauth_nonce);

        System.out.println("URL: " + url);

        System.out.println("AUTH HEADER: " + authorization_header_string);

        ResponseLatch latch = new ResponseLatch();
        RH rh = new RH();

        System.out.println("MAKING REQUEST TO " + url);

        client.get().setURL(url).addHeader(Headers.stringHeader("Authorization"), authorization_header_string)
                .addHeader(Headers.stringHeader("Accept"), "*/*")
                .addHeader(Headers.stringHeader("Content-Type"), "application/x-www-form-urlencoded")
                //                .setBody("screen_name=kablosna", MediaType.PLAIN_TEXT_UTF_8)
                .on(StateType.Closed, latch)
                .execute(rh);
        rh.await(1, TimeUnit.MINUTES);
        latch.latch.await(1, TimeUnit.MINUTES);

        String responseBody = rh.getResponse();

        System.out.println("RESPONSE BODY IS: " + responseBody);

        RUI rui = new RUI();
        if (responseBody == null) {
//            System.out.println("NULL RESPONSE BODY.");
            throw new IOException(rh.toString());
        }
        rui.putAll(new ObjectMapper().readValue(responseBody, Map.class));

//        String url = "https://api.twitter.com/1.1/users/show.json?user_id=" + screenName + "&screen_name="
//                + screenName;
//        System.out.println("Fetch uer info for " + screenName);
        return rui;
    }

    private static final String ALGORITHM = "HmacSHA1";
    
    String generateSignature(String data, AuthorizationResponse token) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] byteHMAC = null;
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec spec;
        if (token == null) {
            String signature = HttpParameter.encode(twitter_consumer_secret) + "&";
            spec = new SecretKeySpec(signature.getBytes(), ALGORITHM);
        } else {
            String signature = HttpParameter.encode(twitter_consumer_secret) 
                    + "&" + HttpParameter.encode(token.accessTokenSecret);
            spec = new SecretKeySpec(signature.getBytes(), ALGORITHM);
        }
        mac.init(spec);
        byteHMAC = mac.doFinal(data.getBytes());
        String sig = BASE64Encoder.encode(byteHMAC);
        System.out.println("TSIGSTRING: " + data);
        System.out.println("TSIGNATURE: " + sig);
        return sig;
    }

    static class RUI extends HashMap<String, Object> implements RemoteUserInfo {

        @Override
        public String userName() {
            return (String) get("name");
        }

        @Override
        public String displayName() {
            return (String) get("displayName");
        }

        @Override
        public Object get(String key) {
            return super.get(key);
        }
    }
}
