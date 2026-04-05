package com.aiplatform.controller;

import com.aiplatform.model.Comment;
import com.aiplatform.model.User;
import com.aiplatform.service.CommunityManagerService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityManagerService communityManagerService;

    @GetMapping("/inbox")
    public ResponseEntity<List<Comment>> getInbox(
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String priority) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        return ResponseEntity.ok(communityManagerService.getInbox(user, sentiment, priority));
    }

    @PostMapping("/sync")
    public ResponseEntity<List<Comment>> syncComments() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        return ResponseEntity.ok(communityManagerService.syncComments(user));
    }

    @PostMapping("/{commentId}/draft")
    public ResponseEntity<Map<String, String>> draftReply(@PathVariable Long commentId) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        String draft = communityManagerService.draftReply(commentId, user);
        return ResponseEntity.ok(Map.of("draft", draft));
    }

    @PostMapping("/{commentId}/reply")
    public ResponseEntity<Void> sendReply(@PathVariable Long commentId, @RequestBody Map<String, String> request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        String replyText = request.get("replyText");
        communityManagerService.sendReply(commentId, replyText, user);
        return ResponseEntity.ok().build();
    }
}
