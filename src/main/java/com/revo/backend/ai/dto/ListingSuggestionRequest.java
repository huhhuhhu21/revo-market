package com.revo.backend.ai.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ListingSuggestionRequest(

        @Size(max = 2000)
        String descriptionInput,

        @Size(max = 8)
        List<String> imageUploadTokens,

        @Positive
        Long listingId

) {}