package com.aiplatform.controller;

import com.aiplatform.dto.FacebookReviewDtos;
import com.aiplatform.model.User;
import com.aiplatform.service.FacebookReviewService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fb-reviews")
@RequiredArgsConstructor
public class FacebookReviewController {

    private final FacebookReviewService reviewService;

    @GetMapping
    public ResponseEntity<List<FacebookReviewDtos.ReviewData>> getReviews() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(reviewService.getPageReviews(user));
    }

    @PostMapping("/generate-reply")
    public ResponseEntity<String> generateReply(@RequestBody FacebookReviewDtos.ReviewData review) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(reviewService.generateAiReply(user, review.getReviewText(), review.getRating()));
    }

    @PostMapping("/reply")
    public ResponseEntity<Void> postReply(@RequestBody FacebookReviewDtos.ReviewReplyRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        reviewService.postReply(user, request.getReviewId(), request.getReplyText());
        return ResponseEntity.ok().build();
    }
}
