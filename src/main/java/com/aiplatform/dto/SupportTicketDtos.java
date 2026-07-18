package com.aiplatform.dto;

import com.aiplatform.model.SupportTicketPriority;
import com.aiplatform.model.SupportTicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class SupportTicketDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupportTicketRequest {
        private String subject;
        private String description;
        private SupportTicketPriority priority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupportTicketResponse {
        private Long id;
        private String subject;
        private String description;
        private SupportTicketStatus status;
        private SupportTicketPriority priority;
        private Long userId;
        private String userFullName;
        private String userEmail;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketReplyRequest {
        private String message;
    }
}
