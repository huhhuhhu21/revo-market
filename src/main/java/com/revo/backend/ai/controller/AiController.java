package com.revo.backend.ai.controller;

import com.revo.backend.ai.dto.ListingSuggestionRequest;
import com.revo.backend.ai.dto.ListingSuggestionResponse;
import com.revo.backend.ai.service.AiSuggestionService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiSuggestionService aiSuggestionService;

    public AiController(
            AiSuggestionService aiSuggestionService
    ) {
        this.aiSuggestionService = aiSuggestionService;
    }

    @PostMapping("/listing-suggestions")
    public ListingSuggestionResponse suggest(
            @Valid @RequestBody ListingSuggestionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.parseLong(jwt.getSubject());

        return aiSuggestionService.suggest(
                userId,
                request
        );
    }
}