package com.aiplatform.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

public class FacebookReviewDtos {
    @Data
    public static class ReviewReplyRequest {
        private String reviewId;
        private String replyText;
    }

    @Data
    @Builder
    public static class ReviewData {
        private String id;
        private String reviewerName;
        private int rating;
        private String reviewText;
        private String createdTime;
    }
}
