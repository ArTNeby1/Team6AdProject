package com.loomytrip.backend.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Transport-friendly wrapper around a Spring Data {@link Page}, so controllers
 * never leak the full {@code Page} serialization (which is unstable across
 * Spring versions) to clients.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> PageResponse<T> from(Page<S> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
