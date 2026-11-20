package com.revo.backend.ai.dto;

import java.util.List;

public record ListingSuggestionResponse(
        String suggestedTitle,
        SuggestedCategory suggestedCategory,
        Long listedPrice,
        String itemCondition,
        List<String> warnings
) {
    public record SuggestedCategory(
            Long id,
            String name
    ) {}
}