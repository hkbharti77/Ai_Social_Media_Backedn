package com.aiplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class PollDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PollData {
        private String question;
        private List<PollOption> options;
        private int durationMinutes = 1440; // Default 24 hours
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PollOption {
        private String text;
    }
}
