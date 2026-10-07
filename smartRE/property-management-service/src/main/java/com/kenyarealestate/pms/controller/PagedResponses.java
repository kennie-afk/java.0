package com.kenyarealestate.pms.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * A bounded page returned as a plain JSON array, with the rest of the list described in
 * headers. The array shape is what existing callers already parse; the headers are what lets a
 * caller tell a full list from one that was cut off.
 */
final class PagedResponses {

    static final int MAX_SIZE = 200;

    private PagedResponses() {}

    static <T> ResponseEntity<List<T>> of(Page<T> page) {
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(page.getTotalElements()))
                .header("X-Has-More", Boolean.toString(page.hasNext()))
                .body(page.getContent());
    }
}
