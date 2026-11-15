package com.closecircle.api.pagination;

import java.util.List;

/**
 * The contract's collection envelope (docs/api-contract.md, section 5).
 * {@code nextCursor} is null on the last page.
 */
public record PageResponse<T>(List<T> items, String nextCursor) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    /** Missing means 50; anything else is clamped to 1-100, as the contract specifies. */
    public static int clampLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, requested));
    }
}
