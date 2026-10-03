package com.mara.core.fiscal;

import com.mara.kit.auth.TerminalAuthFilter;
import com.mara.kit.auth.TerminalRecord;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The terminal's fiscal number endpoints. Numbers travel as strings: they may exceed 2^53. */
@RestController
public class FiscalController {

    private final FiscalLeaseService leases;

    public FiscalController(FiscalLeaseService leases) {
        this.leases = leases;
    }

    private static final Pattern REQUEST_KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    /**
     * Asks for a block of fiscal numbers. {@code ?request=<id>} (part of the signed path) names
     * the request so a retry returns the same lease; without it the request's signature does,
     * which makes a replay of a captured request harmless. A replay answers 200, a new lease 201.
     */
    @PostMapping("/v1/terminal/fiscal/leases")
    public ResponseEntity<Map<String, Object>> lease(
            @RequestParam(name = "request", required = false) String requestId, HttpServletRequest request) {
        TerminalRecord t = (TerminalRecord) request.getAttribute(TerminalAuthFilter.TERMINAL_ATTRIBUTE);
        String key;
        if (requestId != null) {
            if (!REQUEST_KEY.matcher(requestId).matches()) {
                throw new FiscalLeaseService.Refused(400, "bad_request_id",
                        "request must be 8 to 64 letters, digits or hyphens");
            }
            key = "id:" + requestId;
        } else {
            // The signature is a function of terminal, second, verb, path and body: identical
            // bytes within the window can only be a retry or a replay.
            key = "sig:" + request.getHeader("X-Mara-Signature");
        }
        FiscalLeaseService.Issued issued = leases.issue(t, key);
        FiscalLeaseService.Lease l = issued.lease();
        return ResponseEntity.status(issued.replayed() ? 200 : 201).body(Map.of(
                "replayed", issued.replayed(),
                "leaseId", String.valueOf(l.leaseId()),
                "terminalId", l.terminalId(),
                "firstNumber", String.valueOf(l.firstNumber()),
                "lastNumber", String.valueOf(l.lastNumber()),
                "nextNumber", String.valueOf(l.nextNumber()),
                "issuedAtMs", l.issuedAtMs(),
                "expiresAtMs", l.expiresAtMs()));
    }

    public record GiveBack(String nextUnused) {
    }

    @PostMapping("/v1/terminal/fiscal/leases/{id}/return")
    public Map<String, Object> giveBack(
            @PathVariable long id, @RequestBody GiveBack body, HttpServletRequest request) {
        TerminalRecord t = (TerminalRecord) request.getAttribute(TerminalAuthFilter.TERMINAL_ATTRIBUTE);
        long next;
        try {
            next = Long.parseLong(body.nextUnused());
        } catch (NumberFormatException | NullPointerException e) {
            throw new FiscalLeaseService.Refused(400, "bad_number", "nextUnused must be a whole number");
        }
        return leases.giveBack(t, id, next);
    }

    @ExceptionHandler(FiscalLeaseService.Refused.class)
    public ResponseEntity<Map<String, String>> refused(FiscalLeaseService.Refused e) {
        return ResponseEntity.status(e.status).body(Map.of("error", e.code, "message", e.getMessage()));
    }
}
