package com.aiplatform.controller;

import com.aiplatform.model.Post;
import com.aiplatform.model.PostStatus;
import com.aiplatform.model.User;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.service.PdfService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final PdfService pdfService;
    private final PostRepository postRepository;

    @GetMapping("/monthly-roi")
    public ResponseEntity<byte[]> downloadMonthlyRoiReport() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        // Fetch this month's posts
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Post> posts = postRepository.findByUserAndStatusAndCreatedAtAfter(user, PostStatus.PUBLISHED, oneMonthAgo);

        // Core ROI Metrics
        int count = posts.size();
        long hoursSaved = count * 45 / 60; // 45 mins per post industry avg
        double reachGrowth = 12.5; // Mock growth for now
        String topPost = posts.isEmpty() ? "" : posts.get(0).getCaption();

        byte[] pdf = pdfService.generateRoiReport(user, count, hoursSaved, reachGrowth, topPost);

        String filename = "VaniAI_ROI_Report_" + LocalDateTime.now().getMonth() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
