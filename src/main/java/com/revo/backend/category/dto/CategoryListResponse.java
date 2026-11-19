
package com.revo.backend.category.dto;

import java.util.List;

public record CategoryListResponse(
        List<CategoryItem> items
) {
    public record CategoryItem(
            Long id,
            String name
    ) {}
}
