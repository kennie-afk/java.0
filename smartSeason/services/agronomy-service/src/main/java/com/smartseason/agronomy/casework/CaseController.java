package com.smartseason.agronomy.casework;

import com.smartseason.agronomy.casework.CaseDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agronomy/v1/cases")
@Tag(name = "Cases", description = "Questions from the field that an agronomist answers")
public class CaseController {

    private static final String ANYONE = "hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'WORKER', 'AGRONOMIST')";
    private static final String STAFF = "hasAnyRole('ADMIN', 'AGRONOMIST')";

    private final CaseService service;

    public CaseController(CaseService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(ANYONE)
    @Operation(summary = "Ask an agronomist; routed to the least-loaded one covering the county")
    public ResponseEntity<CaseDetail> raise(@Valid @RequestBody RaiseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.raise(Caller.current(), request));
    }

    @GetMapping("/mine")
    @PreAuthorize(ANYONE)
    @Operation(summary = "My questions, newest first; the owner may pass scope=ORG for the whole organisation")
    public CasePage mine(@RequestParam(defaultValue = "MINE") String scope,
                         @RequestParam(required = false) String cursor,
                         @RequestParam(defaultValue = "20") int size) {
        return service.mine(Caller.current(), "ORG".equalsIgnoreCase(scope), cursor, size);
    }

    @GetMapping("/queue")
    @PreAuthorize(STAFF)
    @Operation(summary = "The agronomist's queue: UNASSIGNED, MINE or ANSWERED, most urgent first")
    public CasePage queue(@RequestParam(defaultValue = "UNASSIGNED") CaseService.Scope scope,
                          @RequestParam(required = false) String cursor,
                          @RequestParam(defaultValue = "20") int size) {
        return service.queue(Caller.current(), scope, cursor, size);
    }

    @GetMapping("/summary")
    @PreAuthorize(ANYONE)
    @Operation(summary = "Counts for the navigation badge")
    public Summary summary() {
        return service.summary(Caller.current());
    }

    @GetMapping("/profile")
    @PreAuthorize(STAFF)
    public ProfileView profile() {
        return service.myProfile(Caller.current());
    }

    @PutMapping("/profile")
    @PreAuthorize(STAFF)
    @Operation(summary = "Set the counties I cover and how many cases I take at once")
    public ProfileView updateProfile(@RequestBody ProfileRequest request) {
        return service.updateProfile(Caller.current(), request);
    }

    @GetMapping("/profiles")
    @PreAuthorize(STAFF)
    public List<ProfileView> profiles() {
        return service.allProfiles(Caller.current());
    }

    @GetMapping("/{id}")
    @PreAuthorize(ANYONE)
    public CaseDetail one(@PathVariable UUID id) {
        return service.get(Caller.current(), id);
    }

    @GetMapping("/{id}/photos/{photoId}")
    @PreAuthorize(ANYONE)
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable UUID photoId) {
        CasePhoto p = service.photo(Caller.current(), id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(p.getData());
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize(STAFF)
    @Operation(summary = "Take an unassigned case; exactly one agronomist wins a simultaneous claim")
    public CaseDetail claim(@PathVariable UUID id) {
        return service.claim(Caller.current(), id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public CaseDetail assign(@PathVariable UUID id, @RequestBody AssignRequest request) {
        return service.assign(Caller.current(), id, request.userId());
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize(ANYONE)
    public CaseDetail reply(@PathVariable UUID id, @Valid @RequestBody ReplyRequest request) {
        return service.reply(Caller.current(), id, request.body());
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(STAFF)
    @Operation(summary = "Answer with a formal advisory recorded against the plot and season")
    public CaseDetail resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
        return service.resolve(Caller.current(), id, request);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(ANYONE)
    public CaseDetail close(@PathVariable UUID id) {
        return service.close(Caller.current(), id);
    }
}
