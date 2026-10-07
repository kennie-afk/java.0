package com.hms.platform.storage;

import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves a stored file to the holder of a signed, expiring link. There is no sign-in here by design (that is what a pre-signed link is),
 * so the link is the credential: it is short-lived, it names one object, and every refusal looks the same so it reveals nothing.
 */
@RestController
class ObjectController {

    private final ObjectStore store;
    private final ObjectLinks links;

    ObjectController(ObjectStore store, ObjectLinks links) {
        this.store = store;
        this.links = links;
    }

    @GetMapping("/v1/objects/{org}/{object}")
    ResponseEntity<byte[]> get(@PathVariable String org, @PathVariable String object, @RequestParam long exp, @RequestParam String t, @RequestParam String sig) {
        if (!links.verify(org + "/" + object, exp, t, sig)) {
            return ResponseEntity.status(404).build();
        }
        Optional<byte[]> bytes = store.read(org + "/" + object);
        if (bytes.isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/" + t))
                // The file is private patient data: never cached, never sniffed into something else, never rendered with scripts.
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(bytes.get());
    }
}
