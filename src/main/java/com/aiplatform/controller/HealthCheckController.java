package com.aiplatform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthCheckController {

    @RequestMapping(value = "/", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Map<String, Object>> rootHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "GyanVaniAi Backend"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP"
        ));
    }
}

