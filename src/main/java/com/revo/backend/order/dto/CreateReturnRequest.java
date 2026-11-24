
package com.revo.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateReturnRequest(

        @NotBlank
        @Size(max = 2000)
        String reason,

        @NotNull
        @Size(min = 1, max = 5)
        List<@NotNull @Valid EvidenceImage> evidenceImages

) {
    public record EvidenceImage(
            @NotBlank String uploadToken
    ) {}
}
