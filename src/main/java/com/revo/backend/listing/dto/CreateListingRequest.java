package com.revo.backend.listing.dto;

import com.revo.backend.listing.entity.ItemCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateListingRequest(

        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @NotNull
        @Positive
        Long categoryId,

        @NotNull
        @DecimalMin("1")
        BigDecimal listedPrice,

        @NotNull
        ItemCondition itemCondition,

        @NotBlank
        @Size(max = 100)
        String province,

        @NotBlank
        @Size(max = 100)
        String ward,

        @NotNull
        @Size(min = 1, max = 8)
        List<@NotNull @Valid ImageInput> images

) {

    public record ImageInput(

            @NotBlank
            String uploadToken,

            @NotNull
            @Min(1)
            @Max(8)
            Integer sortOrder

    ) {}
}