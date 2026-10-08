package com.chubb.notification.controller;

import com.chubb.notification.dto.NotificationResponse;
import com.chubb.notification.service.NotificationService;
import com.chubb.platform.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;

    /** Claimants always get their own notifications; staff may filter by claimantEmail or list everything. */
    @GetMapping
    @Operation(summary = "Notifications sent to the claimant (claimants: their own; staff: all or by claimantEmail)")
    public ResponseEntity<PageBody> list(Authentication auth,
                                         @RequestParam(required = false) String claimantEmail,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        Actor actor = Actor.from(auth);
        Page<NotificationResponse> result;
        if (actor.isClaimant()) {
            result = service.forRecipient(actor.username(), page, size);
        } else if (claimantEmail != null && !claimantEmail.isBlank()) {
            result = service.forRecipient(claimantEmail, page, size);
        } else {
            result = service.all(page, size);
        }
        return ResponseEntity.ok(new PageBody(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    public record PageBody(List<NotificationResponse> items, int page, int size, long totalItems, int totalPages) {
    }
}
