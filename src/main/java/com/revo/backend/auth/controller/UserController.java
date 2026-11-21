
package com.revo.backend.auth.controller;

import com.revo.backend.auth.dto.ChangePasswordRequest;
import com.revo.backend.auth.dto.PrivateUserResponse;
import com.revo.backend.auth.dto.PublicUserProfileResponse;
import com.revo.backend.auth.service.AuthService;

import jakarta.validation.Valid;

import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        Long userId = Long.parseLong(jwt.getSubject());

        authService.changePassword(userId, request);
    }


    @GetMapping("/me")
    public PrivateUserResponse getMyProfile(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.parseLong(
                jwt.getSubject()
        );

        return authService.getMyProfile(userId);
    }


    @PatchMapping("/me")
    public PrivateUserResponse updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JsonNode request
    ) {
        Long userId = Long.parseLong(
                jwt.getSubject()
        );

        return authService.updateMyProfile(
                userId,
                request
        );
    }


    @GetMapping("/{userId}/public-profile")
    public PublicUserProfileResponse getPublicProfile(
            @PathVariable("userId") Long userId
    ) {
        return authService.getPublicProfile(userId);
    }



}
