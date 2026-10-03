package com.hms.fhir;

import static com.hms.fhir.FhirService.obj;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** The FHIR R4 read interface. JSON only (application/fhir+json); no create, update or delete exists. */
@RestController
@RequestMapping(value = "/fhir/r4", produces = FhirController.FHIR_JSON)
class FhirController {

    static final String FHIR_JSON = "application/fhir+json";

    private static final String PATIENT = "hasAuthority('fhir:read') and hasAuthority('patients:read')";
    private static final String CLINICAL = "hasAuthority('fhir:read') and hasAuthority('patients:read') and hasAuthority('clinical:read')";

    private final FhirService fhir;

    FhirController(FhirService fhir) {
        this.fhir = fhir;
    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(FHIR_JSON)).body(body);
    }

    @GetMapping("/metadata")
    @PreAuthorize("hasAuthority('fhir:read')")
    ResponseEntity<Map<String, Object>> metadata() {
        List<Map<String, Object>> resources = new ArrayList<>();
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("Patient", List.of("_id", "identifier", "name", "birthdate"));
        params.put("Encounter", List.of("patient"));
        params.put("Observation", List.of("patient", "category"));
        params.put("MedicationRequest", List.of("patient"));
        params.put("AllergyIntolerance", List.of("patient"));
        params.forEach((type, ps) -> {
            List<Map<String, Object>> sp = new ArrayList<>();
            ps.forEach(n -> sp.add(obj("name", n, "type", switch (n) {
                case "identifier" -> "token";
                case "patient" -> "reference";
                case "birthdate" -> "date";
                case "category" -> "token";
                case "_id" -> "token";
                default -> "string";
            })));
            resources.add(obj("type", type, "interaction", List.of(obj("code", "read"), obj("code", "search-type")), "searchParam", sp));
        });
        Map<String, Object> cs = new LinkedHashMap<>();
        cs.put("resourceType", "CapabilityStatement");
        cs.put("status", "active");
        cs.put("date", Instant.now().toString());
        cs.put("kind", "instance");
        cs.put("software", obj("name", "HMS FHIR read interface"));
        cs.put("fhirVersion", "4.0.1");
        cs.put("format", List.of("json"));
        cs.put("rest", List.of(obj("mode", "server", "documentation", "Read-only. Not claimed to conform to any national implementation guide.", "resource", resources)));
        return ok(cs);
    }

    @GetMapping("/Patient/{id}")
    @PreAuthorize(PATIENT)
    ResponseEntity<Map<String, Object>> patient(@PathVariable String id, @RequestHeader(name = "X-Access-Reason", required = false) String reason) {
        return ok(fhir.patient(id, reason));
    }

    @GetMapping("/Patient")
    @PreAuthorize(PATIENT)
    ResponseEntity<Map<String, Object>> patients(@RequestParam(required = false) String identifier, @RequestParam(required = false) String name, @RequestParam(required = false) String birthdate,
                                                 @RequestParam(name = "_id", required = false) String id, @RequestParam(name = "_cursor", required = false) String cursor,
                                                 @RequestParam(name = "_count", required = false) Integer count, HttpServletRequest request) {
        return ok(bundle(request, fhir.patients(identifier, name, birthdate, id, cursor, count)));
    }

    @GetMapping("/Encounter/{id}")
    @PreAuthorize(CLINICAL)
    ResponseEntity<Map<String, Object>> encounter(@PathVariable String id, @RequestHeader(name = "X-Access-Reason", required = false) String reason) {
        return ok(fhir.encounter(id, reason));
    }

    @GetMapping("/Encounter")
    @PreAuthorize(CLINICAL)
    ResponseEntity<Map<String, Object>> encounters(@RequestParam(required = false) String patient, @RequestParam(name = "_cursor", required = false) String cursor,
                                                   @RequestParam(name = "_count", required = false) Integer count, @RequestHeader(name = "X-Access-Reason", required = false) String reason,
                                                   HttpServletRequest request) {
        return ok(bundle(request, fhir.encounters(patient, cursor, count, reason)));
    }

    @GetMapping("/Observation/{id}")
    @PreAuthorize(CLINICAL)
    ResponseEntity<Map<String, Object>> observation(@PathVariable String id, @RequestHeader(name = "X-Access-Reason", required = false) String reason) {
        return ok(fhir.observation(id, reason));
    }

    @GetMapping("/Observation")
    @PreAuthorize(CLINICAL)
    ResponseEntity<Map<String, Object>> observations(@RequestParam(required = false) String patient, @RequestParam(required = false) String category,
                                                     @RequestParam(name = "_cursor", required = false) String cursor, @RequestParam(name = "_count", required = false) Integer count,
                                                     @RequestHeader(name = "X-Access-Reason", required = false) String reason, HttpServletRequest request) {
        return ok(bundle(request, fhir.observations(patient, category, cursor, count, reason)));
    }

    @GetMapping("/MedicationRequest/{id}")
    @PreAuthorize(CLINICAL)
    ResponseEntity<Map<String, Object>> medicationRequest(@PathVariable String id, @RequestHeader(name = "X-Access-Reason", required = false) String reason) {
        return ok(fhir.medicationRequest(id, reason));
    }

    @GetMapping("/MedicationRequest")
    @PreAuthorize(CLINICAL)
    ResponseEntity<Map<String, Object>> medicationRequests(@RequestParam(required = false) String patient, @RequestParam(name = "_cursor", required = false) String cursor,
                                                           @RequestParam(name = "_count", required = false) Integer count, @RequestHeader(name = "X-Access-Reason", required = false) String reason,
                                                           HttpServletRequest request) {
        return ok(bundle(request, fhir.medicationRequests(patient, cursor, count, reason)));
    }

    @GetMapping("/AllergyIntolerance/{id}")
    @PreAuthorize(CLINICAL)
    ResponseEntity<Map<String, Object>> allergy(@PathVariable String id, @RequestHeader(name = "X-Access-Reason", required = false) String reason) {
        return ok(fhir.allergy(id, reason));
    }

    @GetMapping("/AllergyIntolerance")
    @PreAuthorize(CLINICAL)
    ResponseEntity<Map<String, Object>> allergies(@RequestParam(required = false) String patient, @RequestParam(name = "_cursor", required = false) String cursor,
                                                  @RequestParam(name = "_count", required = false) Integer count, @RequestHeader(name = "X-Access-Reason", required = false) String reason,
                                                  HttpServletRequest request) {
        return ok(bundle(request, fhir.allergies(patient, cursor, count, reason)));
    }

    /** A searchset Bundle. The self and next links are built from the request URL; the cursor is opaque. */
    private static Map<String, Object> bundle(HttpServletRequest request, FhirService.Page page) {
        String base = ServletUriComponentsBuilder.fromContextPath(request).path("/fhir/r4").toUriString();
        List<Map<String, Object>> links = new ArrayList<>();
        links.add(obj("relation", "self", "url", ServletUriComponentsBuilder.fromRequest(request).toUriString()));
        if (page.nextCursor() != null) {
            links.add(obj("relation", "next", "url", ServletUriComponentsBuilder.fromRequest(request).replaceQueryParam("_cursor", page.nextCursor()).build().toUriString()));
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> r : page.resources()) {
            entries.add(obj("fullUrl", base + "/" + r.get("resourceType") + "/" + r.get("id"), "resource", r, "search", obj("mode", "match")));
        }
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("resourceType", "Bundle");
        b.put("type", "searchset");
        b.put("timestamp", Instant.now().toString());
        b.put("link", links);
        b.put("entry", entries);
        return b;
    }
}
