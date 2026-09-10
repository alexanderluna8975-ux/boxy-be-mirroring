package com.boxy.boxy.core.web;

import com.boxy.boxy.core.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Builds a validated {@link Pageable} from the query params the FE actually sends
 * (1-based {@code page}, {@code pageSize} with {@code limit} as its legacy alias, and
 * {@code sortField}/{@code sortDirection}). Centralizing this avoids each controller
 * repeating its own ad-hoc {@code page - 1} / {@code Math.max(limit, pageSize)} logic
 * and the {@link IllegalArgumentException} that a bad {@code page} or {@code limit}
 * used to bubble up as an opaque 500.
 */
public final class PageableFactory {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PageableFactory() {
    }

    /** No sorting support — for endpoints the FE doesn't sort. */
    public static Pageable of(int page, Integer limit, Integer pageSize) {
        return of(page, limit, pageSize, null, null, Map.of());
    }

    /**
     * @param sortFieldAllowlist maps the FE's {@code sortField} value to the actual
     *                           entity property/column to sort by. A {@code sortField}
     *                           outside this map is silently ignored rather than
     *                           passed straight into a JPQL {@code ORDER BY}.
     */
    public static Pageable of(int page, Integer limit, Integer pageSize, String sortField, String sortDirection,
                               Map<String, String> sortFieldAllowlist) {
        if (page < 1) {
            throw new BusinessException("INVALID_PAGE", "page must be >= 1.", HttpStatus.BAD_REQUEST);
        }

        int size = pageSize != null ? pageSize : (limit != null ? limit : DEFAULT_PAGE_SIZE);
        if (size < 1) {
            throw new BusinessException("INVALID_PAGE_SIZE", "pageSize must be >= 1.", HttpStatus.BAD_REQUEST);
        }
        size = Math.min(size, MAX_PAGE_SIZE);

        Sort sort = Sort.unsorted();
        String column = sortField != null ? sortFieldAllowlist.get(sortField) : null;
        if (column != null) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(direction, column);
        }

        return PageRequest.of(page - 1, size, sort);
    }
}
