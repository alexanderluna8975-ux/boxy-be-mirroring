package com.boxy.boxy.core.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageMeta {
    private int page;
    private int limit;
    private long totalItems;
    private int totalPages;
    private boolean hasNextPage;
    private boolean hasPreviousPage;

    public static PageMeta of(int page, int limit, long totalItems) {
        int totalPages = limit > 0 ? (int) Math.ceil((double) totalItems / limit) : 0;
        return PageMeta.builder()
                .page(page)
                .limit(limit)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .hasNextPage(page < totalPages)
                .hasPreviousPage(page > 1)
                .build();
    }
}
