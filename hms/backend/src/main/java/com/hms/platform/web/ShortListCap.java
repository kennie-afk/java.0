package com.hms.platform.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * A hard ceiling for the lists that are returned whole because they are bounded by a catalogue (wards, beds, clinics, lab tests). They are not
 * paged, so if one ever grows past the ceiling the caller gets a clear error instead of a slow or silently truncated answer.
 * Everything that grows with patients or visits is paged by keyset instead and never goes through here.
 */
@Component
public class ShortListCap {

    private final int cap;

    public ShortListCap(@Value("${hms.limits.catalogue-cap:1000}") int cap) {
        this.cap = cap;
    }

    /** Rows to ask the database for: one more than the cap, so an excess is noticed. */
    public int fetchLimit() {
        return cap + 1;
    }

    /** Throws when the list went past the cap. */
    public <T> java.util.List<T> check(java.util.List<T> rows, String what) {
        if (rows.size() > cap) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "list_too_large",
                    "There are more than " + cap + " " + what + ", which is more than this list shows at once. Narrow it down or ask your administrator to raise HMS_CATALOGUE_CAP.");
        }
        return rows;
    }
}
