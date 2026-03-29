package com.aiplatform.dto;

import lombok.Data;

public class MicrositeDtos {
    @Data
    public static class LinkCreateRequest {
        private String title;
        private String url;
    }
}
