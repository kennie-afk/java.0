package com.smartseason.telemetryingest.platform;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    public static <T> PageResponse<T> of(Slice<T> slice, long totalElements) {
        int size = slice.getSize();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                slice.getContent(),
                slice.getNumber(),
                size,
                totalElements,
                totalPages,
                slice.getNumber() == 0,
                !slice.hasNext());
    }
}
