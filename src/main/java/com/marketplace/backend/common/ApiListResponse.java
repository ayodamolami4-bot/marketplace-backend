package com.marketplace.backend.common;

import java.util.List;

public record ApiListResponse<T>(
        List<T> data,
        int page,
        int pageSize,
        long total
) {
}