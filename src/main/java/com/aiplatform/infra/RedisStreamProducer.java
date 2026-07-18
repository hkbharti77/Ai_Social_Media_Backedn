package com.aiplatform.infra;

import com.aiplatform.dto.AiRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * RedisStreamProducer - Dispatches AI tasks to a distributed Redis Stream.
 */
@Service
@RequiredArgsConstructor
public class RedisStreamProducer {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String STREAM_KEY = "ai:jobs:stream";

    public void produce(AiRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            ObjectRecord<String, String> record = StreamRecords.newRecord()
                    .in(STREAM_KEY)
                    .ofObject(json);
            
            redisTemplate.opsForStream().add(record);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AI request for Redis Stream", e);
        }
    }
}
