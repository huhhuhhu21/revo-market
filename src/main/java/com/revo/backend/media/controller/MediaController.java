package com.revo.backend.media.controller;

import com.revo.backend.media.dto.MediaUploadResponse;
import com.revo.backend.media.service.MediaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }


    @PostMapping(
            value = "/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "LISTING_IMAGE")
            String purpose,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.parseLong(jwt.getSubject());

        return mediaService.upload(userId, file, purpose);
    }

}