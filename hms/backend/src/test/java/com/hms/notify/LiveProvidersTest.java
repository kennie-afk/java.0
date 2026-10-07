package com.hms.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The live providers against stubs that imitate Africa's Talking's documented answer and a bare SMTP server. Never run against the real services. */
class LiveProvidersTest {

    private static NotificationProvider.Outbound sms(String to) {
        return new NotificationProvider.Outbound(UUID.randomUUID(), "SMS", to, "subject", "Your portal account was created.");
    }

    // ---- Africa's Talking ---------------------------------------------------------------------

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> reply = new AtomicReference<>();
    private final AtomicReference<Integer> httpStatus = new AtomicReference<>(201);

    private HttpServer atStub() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/version1/messaging", ex -> {
            requests.add(ex.getRequestHeaders().getFirst("apiKey") + "|" + ex.getRequestHeaders().getFirst("Content-Type") + "|" + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(httpStatus.get(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        s.start();
        return s;
    }

    private static String atReply(int code, String status) {
        return "{\"SMSMessageData\":{\"Message\":\"Sent to 1/1\",\"Recipients\":[{\"statusCode\":" + code + ",\"number\":\"+254712345678\",\"cost\":\"KES 0.8000\",\"status\":\"" + status + "\",\"messageId\":\"ATXid_1\"}]}}";
    }

    @Test
    void africasTalkingSendsTheDocumentedRequestAndClassifiesFailures() throws Exception {
        HttpServer stub = atStub();
        try {
            var p = new AfricasTalkingSmsProvider("http://127.0.0.1:" + stub.getAddress().getPort(), "sandbox", "key-123", "HMS");
            reply.set(atReply(101, "Success"));
            p.send(sms("0712 345 678"));
            assertThat(requests.get(0)).startsWith("key-123|application/x-www-form-urlencoded|").contains("username=sandbox").contains("to=%2B254712345678")
                    .contains("message=Your+portal+account+was+created.").contains("from=HMS");

            // A number the operator says can never work is permanent; a rejection by the gateway or an outage is worth retrying.
            reply.set(atReply(403, "InvalidPhoneNumber"));
            assertThatThrownBy(() -> p.send(sms("0712345678"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isTrue());
            reply.set(atReply(502, "RejectedByGateway"));
            assertThatThrownBy(() -> p.send(sms("0712345678"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isFalse());
            httpStatus.set(500);
            reply.set("oops");
            assertThatThrownBy(() -> p.send(sms("0712345678"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isFalse());
            // Wrong credentials: retrying cannot help.
            httpStatus.set(401);
            assertThatThrownBy(() -> p.send(sms("0712345678"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isTrue());
            // Not a Kenyan number: nothing is sent at all.
            int before = requests.size();
            assertThatThrownBy(() -> p.send(sms("12345"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isTrue());
            assertThat(requests).hasSize(before);
        } finally {
            stub.stop(0);
        }
    }

    @Test
    void africasTalkingUnreachableIsTransient() {
        var p = new AfricasTalkingSmsProvider("http://127.0.0.1:1", "u", "k", "");
        assertThatThrownBy(() -> p.send(sms("0712345678"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isFalse());
    }

    // ---- SMTP ---------------------------------------------------------------------------------

    /** Just enough SMTP: EHLO, AUTH PLAIN, MAIL, RCPT, DATA, QUIT. Records each accepted message. */
    private static final class FakeSmtp implements AutoCloseable {
        final ServerSocket server;
        final List<String> messages = new CopyOnWriteArrayList<>();
        final String user = "mailer";
        final String pass = "s3cret";
        volatile String rejectRecipient;

        FakeSmtp() throws Exception {
            server = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress());
            Thread t = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket c = server.accept()) {
                        serve(c);
                    } catch (Exception e) {
                        // closed
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        void serve(Socket c) throws Exception {
            BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(c.getOutputStream(), true, StandardCharsets.UTF_8);
            out.print("220 fake ESMTP\r\n");
            out.flush();
            boolean authed = false;
            String line;
            StringBuilder data = null;
            while ((line = in.readLine()) != null) {
                if (data != null) {
                    if (line.equals(".")) {
                        messages.add(data.toString());
                        data = null;
                        reply(out, "250 queued");
                    } else {
                        data.append(line).append('\n');
                    }
                    continue;
                }
                String u = line.toUpperCase();
                if (u.startsWith("EHLO")) {
                    out.print("250-fake\r\n250-AUTH PLAIN\r\n250 8BITMIME\r\n");
                    out.flush();
                } else if (u.startsWith("AUTH PLAIN")) {
                    String[] parts = new String(Base64.getDecoder().decode(line.substring(11).trim()), StandardCharsets.UTF_8).split("\0");
                    authed = parts.length == 3 && parts[1].equals(user) && parts[2].equals(pass);
                    reply(out, authed ? "235 ok" : "535 bad credentials");
                } else if (u.startsWith("MAIL FROM")) {
                    reply(out, authed ? "250 ok" : "530 authentication required");
                } else if (u.startsWith("RCPT TO")) {
                    reply(out, rejectRecipient != null && line.contains(rejectRecipient) ? "550 no such user" : "250 ok");
                } else if (u.startsWith("DATA")) {
                    reply(out, "354 go");
                    data = new StringBuilder();
                } else if (u.startsWith("QUIT")) {
                    reply(out, "221 bye");
                    return;
                } else {
                    reply(out, "250 ok");
                }
            }
        }

        private static void reply(PrintWriter out, String s) {
            out.print(s + "\r\n");
            out.flush();
        }

        int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            server.close();
        }
    }

    private static NotificationProvider.Outbound mail(String to) {
        return new NotificationProvider.Outbound(UUID.randomUUID(), "EMAIL", to, "Your portal account", "Habari Amina, your account was created.");
    }

    @Test
    void smtpDeliversAuthenticatedAndClassifiesFailures() throws Exception {
        try (FakeSmtp smtp = new FakeSmtp()) {
            var ok = new SmtpEmailProvider(new SmtpEmailProvider.Config("127.0.0.1", smtp.port(), "mailer", "s3cret", "hms@example.org", false, false));
            ok.send(mail("amina@example.org"));
            assertThat(smtp.messages).hasSize(1);
            assertThat(smtp.messages.get(0)).contains("Subject: Your portal account").contains("To: amina@example.org").contains("From: hms@example.org").contains("Habari Amina");

            // A recipient the server refuses is permanent; a malformed address never reaches the server.
            smtp.rejectRecipient = "gone@example.org";
            assertThatThrownBy(() -> ok.send(mail("gone@example.org"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isTrue());
            assertThatThrownBy(() -> ok.send(mail("not an address"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isTrue());

            // Wrong password: permanent. Server down: transient.
            var wrong = new SmtpEmailProvider(new SmtpEmailProvider.Config("127.0.0.1", smtp.port(), "mailer", "nope", "hms@example.org", false, false));
            assertThatThrownBy(() -> wrong.send(mail("amina@example.org"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isTrue());
            assertThat(smtp.messages).hasSize(1);
        }
        var down = new SmtpEmailProvider(new SmtpEmailProvider.Config("127.0.0.1", 1, "mailer", "s3cret", "hms@example.org", false, false));
        assertThatThrownBy(() -> down.send(mail("amina@example.org"))).isInstanceOfSatisfying(NotificationProvider.DeliveryException.class, e -> assertThat(e.permanent()).isFalse());
    }
}
