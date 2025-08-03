package com.twilio;

import com.twilio.jwt.accesstoken.AccessToken;
import com.twilio.jwt.accesstoken.VoiceGrant;
import com.twilio.type.*;
import com.twilio.twiml.voice.Client;
import com.twilio.twiml.voice.Dial;
import com.twilio.twiml.voice.Number;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Say;
import com.twilio.twiml.TwiMLException;
import com.twilio.http.TwilioRestClient;
import com.twilio.http.HttpMethod;
import com.twilio.rest.api.v2010.account.Call;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import java.net.URI;

import io.javalin.Javalin;
import io.javalin.http.Context;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.Charset;
import java.lang.Character;

public class Webapp {

    public static class MemorySector {
        private final int startIndex;
        private final int endIndex;
        private final List<Integer> memory;

        public MemorySector(int startIndex, int endIndex, List<Integer> memory) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.memory = memory;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        public List<Integer> getMemory() {
            return memory;
        }
    }

    static final String IDENTITY = "alice";
    static final String CALLER_ID = "client:quick_start";
    // Use a valid Twilio number by adding to your account via https://www.twilio.com/console/phone-numbers/verified
    static final String CALLER_NUMBER = "+351 939148203";

    public static void main(String[] args) throws Exception {
        // Load the .env file into environment
        dotenv();

        // Create Javalin app
        Javalin app = Javalin.create().start(4567);

        // Add CORS headers manually
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        app.options("/*", ctx -> {
            ctx.status(200);
        });

        // Add custom logging filter
        app.after(ctx -> {
            LoggingFilter.logRequestResponse(ctx);
        });

        app.get("/", ctx -> {
            ctx.result(welcome());
        });

        app.post("/", ctx -> {
           ctx.result(welcome());
        });

        /**
         * Creates an access token with VoiceGrant using your Twilio credentials.
         *
         * @returns The Access Token string
         */
        app.get("/accessToken", ctx -> {
            // Read the identity param provided
            final String identity = ctx.queryParam("identity") != null ? ctx.queryParam("identity") : IDENTITY;
            ctx.result(getAccessToken(identity));
        });

        /**
         * Creates an access token with VoiceGrant using your Twilio credentials.
         *
         * @returns The Access Token string
         */
        app.post("/accessToken", ctx -> {
            // Read the identity param provided
            String identity = null;
            List<NameValuePair> pairs = URLEncodedUtils.parse(ctx.body(), Charset.defaultCharset());
            Map<String, String> params = toMap(pairs);
            try {
                identity = params.get("identity");
            } catch (Exception e) {
                ctx.result("Error: " + e.getMessage());
                return;
            }
            ctx.result(getAccessToken(identity != null ? identity : IDENTITY));
        });

        /**
         * Creates an endpoint that can be used in your TwiML App as the Voice Request Url.
         * <br><br>
         * In order to make an outgoing call using Twilio Voice SDK, you need to provide a
         * TwiML App SID in the Access Token. You can run your server, make it publicly
         * accessible and use `/makeCall` endpoint as the Voice Request Url in your TwiML App.
         * <br><br>
         *
         * @returns The TwiMl used to respond to an outgoing call
         */
        app.get("/makeCall", ctx -> {
            final String to = ctx.queryParam("to");
            String from = ctx.queryParam("from");
//            if (from != null && from.startsWith("client:")) {
//                from = from.replaceFirst("client:", "");
//            }
            System.out.printf("From: %s, To: %s%n", from, to);
            ctx.result(call(to, from));
        });

        /**
         * Creates an endpoint that can be used in your TwiML App as the Voice Request Url.
         * <br><br>
         * In order to make an outgoing call using Twilio Voice SDK, you need to provide a
         * TwiML App SID in the Access Token. You can run your server, make it publicly
         * accessible and use `/makeCall` endpoint as the Voice Request Url in your TwiML App.
         *
         * <br><br>
         *
         * @returns The TwiMl used to respond to an outgoing call
         */
        app.post("/makeCall", ctx -> {
            String to = "";
            String from = "";
            List<NameValuePair> pairs = URLEncodedUtils.parse(ctx.body(), Charset.defaultCharset());
            Map<String, String> params = toMap(pairs);
            try {
                to = params.get("to");
                from = params.get("From");
            } catch (Exception e) {
                ctx.result("Error: " + e.getMessage());
                return;
            }
//            if (from.startsWith("client:")) {
//                from = from.replaceFirst("client:", "");
//            }
            System.out.printf("From: %s, To: %s%n", from, to);
            ctx.result(call(to, from));
        });

        app.post("/callAlice", ctx -> {
            String from = "";
            List<NameValuePair> pairs = URLEncodedUtils.parse(ctx.body(), Charset.defaultCharset());
            Map<String, String> params = toMap(pairs);
            try {
                from = params.get("From");
            } catch (Exception e) {
                ctx.result("Error: " + e.getMessage());
                return;
            }

            Client client = new Client.Builder("alice").build();
            Dial dial = new Dial.Builder().callerId(from).timeout(30).client(client)
                    .build();
            VoiceResponse voiceResponse = new VoiceResponse.Builder().dial(dial).build();
            ctx.result(voiceResponse.toXml());
        });

        /**
         * Makes a call to the specified client using the Twilio REST API.
         *
         * @returns The CallSid
         */
        app.get("/placeCall", ctx -> {
            final String to = ctx.queryParam("to");
            // The fully qualified URL that should be consulted by Twilio when the call connects.
            URI uri = URI.create(ctx.scheme() + "://" + ctx.host() + "/incoming");
            System.out.println(uri.toURL().toString());
            ctx.result(callUsingRestClient(to, uri));
        });

        /**
         * Makes a call to the specified client using the Twilio REST API.
         *
         * @returns The CallSid
         */
        app.post("/placeCall", ctx -> {
            String to = "";
            List<NameValuePair> pairs = URLEncodedUtils.parse(ctx.body(), Charset.defaultCharset());
            Map<String, String> params = toMap(pairs);
            try {
                to = params.get("to");
            } catch (Exception e) {
                ctx.result("Error: " + e.getMessage());
                return;
            }
            // The fully qualified URL that should be consulted by Twilio when the call connects.
            URI uri = URI.create(ctx.scheme() + "://" + ctx.host() + "/incoming");
            System.out.println(uri.toURL().toString());
            ctx.result(callUsingRestClient(to, uri));
        });

        /**
         * Creates an endpoint that plays back a greeting.
         */
        app.get("/incoming", ctx -> {
            ctx.result(greet());
        });

        /**
         * Creates an endpoint that plays back a greeting.
         */
        app.post("/incoming", ctx -> {
            ctx.result(greet());
        });
    }

    private static String getAccessToken(final String identity) {
        // Create Voice grant
        VoiceGrant grant = new VoiceGrant();
        grant.setOutgoingApplicationSid(System.getProperty("APP_SID"));
        grant.setPushCredentialSid(System.getProperty("PUSH_CREDENTIAL_SID"));

        // Create access token
        AccessToken token = new AccessToken.Builder(
                System.getProperty("ACCOUNT_SID"),
                System.getProperty("API_KEY_SID"),
                System.getProperty("API_SECRET").getBytes()
        ).identity(identity).grant(grant).build();
        System.out.println(token.toJwt());
        return token.toJwt();
    }

    private static String call(final String to, final String from) {
        VoiceResponse voiceResponse;
        String toXml = null;
        if (to == null || to.isEmpty()) {
            Say say = new Say.Builder("Congratulations! You have made your first call! Good bye.").build();
            voiceResponse = new VoiceResponse.Builder().say(say).build();
        } else if (isPhoneNumber(to)) {
            Number number = new Number.Builder(to).build();
            Dial dial = new Dial.Builder().callerId(CALLER_NUMBER).number(number)
                    .build();
            voiceResponse = new VoiceResponse.Builder().dial(dial).build();
        } else {
            Client client = new Client.Builder(to).build();
            Dial dial = new Dial.Builder().callerId(from).timeout(30).client(client)
                    .build();
            voiceResponse = new VoiceResponse.Builder().dial(dial).build();
        }
        try {
            toXml = voiceResponse.toXml();
        } catch (TwiMLException e) {
            e.printStackTrace();
        }
        return toXml;
    }

    private static String callUsingRestClient(final String to, final URI uri) {
        final TwilioRestClient client = new TwilioRestClient.Builder(System.getProperty("API_KEY"), System.getProperty("API_SECRET"))
                .accountSid(System.getProperty("ACCOUNT_SID"))
                .build();

        if (to == null || to.isEmpty()) {
            com.twilio.type.Client clientEndpoint = new com.twilio.type.Client("client:" + IDENTITY);
            PhoneNumber from = new PhoneNumber(CALLER_ID);
            // Make the call
            Call call = Call.creator(clientEndpoint, from, uri).setMethod(HttpMethod.GET).create(client);
            // Print the call SID (a 32 digit hex like CA123..)
            System.out.println(call.getSid());
            return call.getSid();
        } else if (isNumeric(to)) {
            com.twilio.type.Client clientEndpoint = new com.twilio.type.Client(to);
            PhoneNumber from = new PhoneNumber(CALLER_NUMBER);
            // Make the call
            Call call = Call.creator(clientEndpoint, from, uri).setMethod(HttpMethod.GET).create(client);
            // Print the call SID (a 32 digit hex like CA123..)
            System.out.println(call.getSid());
            return call.getSid();
        } else {
            com.twilio.type.Client clientEndpoint = new com.twilio.type.Client("client:" + to);
            PhoneNumber from = new PhoneNumber(CALLER_ID);
            // Make the call
            Call call = Call.creator(clientEndpoint, from, uri).setMethod(HttpMethod.GET).create(client);
            // Print the call SID (a 32 digit hex like CA123..)
            System.out.println(call.getSid());
            return call.getSid();
        }
    }

    private static String greet() {
        VoiceResponse voiceResponse;
        Say say = new Say.Builder("Congratulations! You have received your first inbound call! Good bye.").build();
        voiceResponse = new VoiceResponse.Builder().say(say).build();
        System.out.println(voiceResponse.toXml().toString());
        return voiceResponse.toXml();
    }

    private static String welcome() {
        VoiceResponse voiceResponse;
        Say say = new Say.Builder("Welcome to Twilio").build();
        voiceResponse = new VoiceResponse.Builder().say(say).build();
        System.out.println(voiceResponse.toXml().toString());
        return voiceResponse.toXml();
    }

    private static void dotenv() throws Exception {
        final File env = new File(".env");
        if (!env.exists()) {
            return;
        }

        final Properties props = new Properties();
        props.load(new FileInputStream(env));
        props.putAll(System.getenv());
        props.entrySet().forEach(p -> System.setProperty(p.getKey().toString(), p.getValue().toString()));
    }

    private static Map<String, String> toMap(final List<NameValuePair> pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.size(); i++) {
            NameValuePair pair = pairs.get(i);
            System.out.println("NameValuePair - name=" + pair.getName() + " value=" + pair.getValue());
            map.put(pair.getName(), pair.getValue());
        }
        return map;
    }

    private static boolean isPhoneNumber(String s) {
        if (s.length() == 1) {
            return isNumeric(s);
        } else if (s.charAt(0) == '+') {
            return isNumeric(s.substring(1));
        } else {
            return isNumeric(s);
        }
    }

    private static boolean isNumeric(String s) {
        int len = s.length();
        for (int i = 0; i < len; ++i) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
