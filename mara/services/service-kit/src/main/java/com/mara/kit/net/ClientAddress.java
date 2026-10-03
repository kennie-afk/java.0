package com.mara.kit.net;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * The address a per-address rate limit should count a request against.
 *
 * <p>By default the socket peer, which a caller cannot choose. Behind the ingress and the terminal
 * proxy every till's request reaches a service from the proxy pod's address, so counting that
 * would put thousands of shops in one bucket and throttle legitimate traffic. With
 * {@code trustForwardedFor} the LAST {@code X-Forwarded-For} entry is used instead: the one the
 * ingress controller appended, which the client cannot forge because entries are only ever added
 * on the right. That is safe only when nothing but the ingress can reach the service (the
 * NetworkPolicy in k8s/ guarantees it), so it is off unless asked for.
 */
public final class ClientAddress {

    private static final Pattern ADDRESS = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request, boolean trustForwardedFor) {
        if (trustForwardedFor) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] parts = xff.split(",");
                String last = parts[parts.length - 1].trim();
                // anything that is not an address is ignored rather than used as a key
                if (ADDRESS.matcher(last).matches()) {
                    return last;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
