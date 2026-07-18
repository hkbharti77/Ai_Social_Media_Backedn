package com.aiplatform.controller;

import com.aiplatform.dto.SupportTicketDtos.SupportTicketRequest;
import com.aiplatform.dto.SupportTicketDtos.SupportTicketResponse;
import com.aiplatform.model.SupportTicketStatus;
import com.aiplatform.model.User;
import com.aiplatform.service.SupportTicketService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final com.aiplatform.service.OwnerSecurityService ownerSecurityService;

    @PostMapping
    public ResponseEntity<SupportTicketResponse> createTicket(@RequestBody SupportTicketRequest request) {
        User currentUser = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(supportTicketService.createTicket(currentUser.getId(), request));
    }

    @GetMapping("/my-tickets")
    public ResponseEntity<List<SupportTicketResponse>> getMyTickets() {
        User currentUser = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(supportTicketService.getUserTickets(currentUser.getId()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupportTicketResponse>> getAllTickets() {
        User currentUser = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        if (!ownerSecurityService.isOwner(currentUser.getEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Strict Owner Access Only");
        }
        return ResponseEntity.ok(supportTicketService.getAllTickets());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportTicketResponse> updateTicketStatus(
            @PathVariable Long id,
            @RequestParam SupportTicketStatus status) {
        User currentUser = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        if (!ownerSecurityService.isOwner(currentUser.getEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Strict Owner Access Only");
        }
        return ResponseEntity.ok(supportTicketService.updateTicketStatus(id, status));
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> replyToTicket(
            @PathVariable Long id,
            @RequestBody com.aiplatform.dto.SupportTicketDtos.TicketReplyRequest request) {
        User currentUser = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        if (!ownerSecurityService.isOwner(currentUser.getEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Strict Owner Access Only");
        }
        supportTicketService.replyToTicket(id, request.getMessage());
        return ResponseEntity.ok().build();
    }
}
