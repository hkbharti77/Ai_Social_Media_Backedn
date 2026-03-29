package com.aiplatform.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedisClient redisClient;

    /**
     * Executes a task within a distributed lock.
     * @param lockKey The unique key for the lock
     * @param timeout How long to wait for the lock
     * @param ttl How long the lock is valid for (to prevent deadlocks)
     * @param task The task to execute
     * @return The result of the task
     */
    public <T> T executeWithLock(String lockKey, Duration timeout, Duration ttl, Supplier<T> task) {
        String fullKey = "lock:" + lockKey;
        String lockValue = java.util.UUID.randomUUID().toString();
        
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> sync = connection.sync();
            
            long end = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < end) {
                // SET key value NX PX ttl
                String result = sync.set(fullKey, lockValue, 
                    io.lettuce.core.SetArgs.Builder.nx().px(ttl.toMillis()));
                
                if ("OK".equals(result)) {
                    try {
                        return task.get();
                    } finally {
                        // Release lock only if we own it (using Lua script for atomicity is better, but this is a start)
                        if (lockValue.equals(sync.get(fullKey))) {
                            sync.del(fullKey);
                        }
                    }
                }
                
                try {
                    Thread.sleep(50); // Retry after 50ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Lock acquisition interrupted", e);
                }
            }
            throw new RuntimeException("Could not acquire lock for " + lockKey + " after " + timeout.toMillis() + "ms");
        }
    }
}
