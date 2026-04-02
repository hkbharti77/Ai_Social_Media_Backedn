package com.aiplatform.dto;

import lombok.Builder;
import lombok.Data;

public class SchedulingDtos {

    @Data
    @Builder
    public static class SuggestedTimes {
        private String morningDraftTime;
        private String eveningDraftTime;
        private String morningPublishTime;
        private String eveningPublishTime;
        private String reason;
    }
}
