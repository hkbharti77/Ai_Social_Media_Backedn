package com.aiplatform.controller;

import com.aiplatform.service.PublisherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug Controller for runtime configuration changes (testing only)
 * 
 * WARNING: This controller should be disabled in production!
 */
@RestController
@RequestMapping("/api/v1/debug")
public class DebugController {

    @Autowired
    private PublisherService publisherService;

    /**
     * Toggle Instagram Reel transcoding bypass
     * 
     * Usage:
     * - Enable bypass: POST /api/v1/debug/reel-transcode-bypass?enabled=true
     * - Disable bypass: POST /api/v1/debug/reel-transcode-bypass?enabled=false
     * - Check status: GET /api/v1/debug/reel-transcode-bypass
     */
    @PostMapping("/reel-transcode-bypass")
    public ResponseEntity<Map<String, Object>> setReelTranscodeBypass(@RequestParam boolean enabled) {
        publisherService.setSkipReelTranscode(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("skipReelTranscode", enabled);
        response.put("message", enabled 
            ? "⚠️ Transcoding BYPASSED - Using original videos" 
            : "✅ Transcoding ENABLED - Videos will be transcoded");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reel-transcode-bypass")
    public ResponseEntity<Map<String, Object>> getReelTranscodeBypass() {
        boolean currentStatus = publisherService.isSkipReelTranscode();
        
        Map<String, Object> response = new HashMap<>();
        response.put("skipReelTranscode", currentStatus);
        response.put("status", currentStatus ? "BYPASSED" : "ENABLED");
        response.put("message", currentStatus 
            ? "⚠️ Transcoding is currently BYPASSED - Using original videos" 
            : "✅ Transcoding is currently ENABLED");
        
        return ResponseEntity.ok(response);
    }
}
