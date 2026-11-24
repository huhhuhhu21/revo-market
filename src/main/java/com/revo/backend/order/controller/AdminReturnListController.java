
package com.revo.backend.order.controller;

import com.revo.backend.order.dto.AdminReturnPageResponse;
import com.revo.backend.order.service.AdminReturnListService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/returns")
public class AdminReturnListController {

    private final AdminReturnListService adminReturnListService;

    public AdminReturnListController(
            AdminReturnListService adminReturnListService
    ) {
        this.adminReturnListService = adminReturnListService;
    }

    @GetMapping
    public AdminReturnPageResponse getReturns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = Long.parseLong(
                jwt.getSubject()
        );

        return adminReturnListService.getReturns(
                adminId,
                page,
                size,
                status,
                orderId
        );
    }
}
