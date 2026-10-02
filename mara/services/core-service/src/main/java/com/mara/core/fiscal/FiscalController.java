package com.mara.core.fiscal;

import com.mara.kit.auth.TerminalAuthFilter;
import com.mara.kit.auth.TerminalRecord;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The terminal's fiscal number endpoints. Numbers travel as strings: they may exceed 2^53. */
@RestController
public class FiscalController {

    private final FiscalLeaseService leases;

    public FiscalController(FiscalLeaseService leases) {
        this.leases = leases;
    }

    @PostMapping("/v1/terminal/fiscal/leases")
    public ResponseEntity<Map<String, Object>> lease(HttpServletRequest request) {
        TerminalRecord t = (TerminalRecord) request.getAttribute(TerminalAuthFilter.TERMINAL_ATTRIBUTE);
        FiscalLeaseService.Lease l = leases.issue(t);
        return ResponseEntity.status(201).body(Map.of(
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
