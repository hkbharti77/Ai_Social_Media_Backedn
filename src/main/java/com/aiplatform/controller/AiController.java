package com.aiplatform.controller;

import com.aiplatform.dto.ContentGenerationDtos.*;
import com.aiplatform.model.BusinessProfile;
import com.aiplatform.model.User;
import com.aiplatform.repository.BusinessProfileRepository;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.security.UserDetailsImpl;
import com.aiplatform.service.AiContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    @Autowired
    private AiContentService aiContentService;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/generate")
    public ResponseEntity<?> generatePosts(@RequestBody PostGenerationRequest request) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).get();
        
        BusinessProfile bp = businessProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Business Profile not found. Please create one first."));

        List<GeneratedPost> posts = new ArrayList<>();
        for (int i = 0; i < request.getCount(); i++) {
            posts.add(aiContentService.generatePost(bp, request.getCommand()));
        }

        return ResponseEntity.ok(new GenerationResponse(posts));
    }
}
