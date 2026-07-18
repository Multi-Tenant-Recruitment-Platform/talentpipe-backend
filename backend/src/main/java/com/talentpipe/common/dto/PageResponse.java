package com.talentpipe.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Uniform pagination envelope returned by every list endpoint, decoupling the
 * API contract from Spring Data's {@link Page} serialization (which is not a
 * stable public format).
 *
 * @param <T> element type — always a DTO, never an entity
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /** Adapts a Spring Data page (already mapped to DTOs) to the API envelope. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /** An empty page — used by endpoints whose backing module ships in a later sprint. */
    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0);
    }
}
