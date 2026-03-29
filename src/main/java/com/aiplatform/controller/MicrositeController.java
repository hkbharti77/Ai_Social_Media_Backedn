package com.aiplatform.controller;

import com.aiplatform.dto.MicrositeDtos.LinkCreateRequest;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.MicrositeLink;
import com.aiplatform.model.User;
import com.aiplatform.service.MicrositeService;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping
@RequiredArgsConstructor
public class MicrositeController {
    private final MicrositeService micrositeService;

    // --- Private Admin API ---
    @GetMapping("/api/v1/microsite/links")
    @ResponseBody
    public ResponseEntity<List<MicrositeLink>> getMyLinks() {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Unauthorized"));
        return ResponseEntity.ok(micrositeService.getLinksForUser(user));
    }

    @PostMapping("/api/v1/microsite/links")
    @ResponseBody
    public ResponseEntity<MicrositeLink> addLink(@RequestBody LinkCreateRequest request) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Unauthorized"));
        return ResponseEntity.ok(micrositeService.addLink(user, request.getTitle(), request.getUrl()));
    }

    @DeleteMapping("/api/v1/microsite/links/{linkId}")
    @ResponseBody
    public ResponseEntity<Void> deleteLink(@PathVariable Long linkId) {
        User user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Unauthorized"));
        micrositeService.deleteLink(linkId, user);
        return ResponseEntity.ok().build();
    }

    // --- Public Profile & Tracking ---
    @GetMapping("/m/{brandSlug}")
    public String renderMicrosite(@PathVariable String brandSlug, Model model) {
        try {
            BusinessProfile profile = micrositeService.getProfileBySlug(brandSlug);
            List<MicrositeLink> links = micrositeService.getPublicLinks(brandSlug);
            
            model.addAttribute("profile", profile);
            model.addAttribute("links", links);
            return "microsite";
        } catch (Exception e) {
            return "error/404";
        }
    }

    @GetMapping("/api/v1/microsite/click/{linkId}")
    @ResponseBody
    public ResponseEntity<Void> trackClick(@PathVariable Long linkId) {
        micrositeService.trackClick(linkId);
        return ResponseEntity.ok().build();
    }
}
