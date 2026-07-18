package com.aiplatform.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class PromptLoader {
    private static final Logger logger = LoggerFactory.getLogger(PromptLoader.class);
    private final ResourceLoader resourceLoader;

    @Cacheable(value = "prompts", key = "#path")
    public String load(String path) {
        String fullPath = "classpath:prompts/" + path + ".st";
        logger.info("Loading prompt template from: {}", fullPath);
        Resource resource = resourceLoader.getResource(fullPath);
        
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load prompt template from {}: {}", fullPath, e.getMessage());
            throw new RuntimeException("Prompt template not found: " + path, e);
        }
    }
}
