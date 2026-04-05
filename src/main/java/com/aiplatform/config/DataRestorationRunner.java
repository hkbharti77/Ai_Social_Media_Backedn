package com.aiplatform.config;

import com.aiplatform.model.Post;
import com.aiplatform.model.User;
import com.aiplatform.repository.PostRepository;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataRestorationRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.security.owner-email}")
    private String ownerEmail;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("🔍 [DataRestoration] Investigating database status...");

        long userCount = userRepository.count();
        long postCount = postRepository.count();

        log.info("📊 [Audit] Total Users: {}", userCount);
        log.info("📊 [Audit] Total Posts: {}", postCount);

        User owner = userRepository.findByEmail(ownerEmail).orElse(null);

        if (owner == null) {
            log.warn("⚠️ [Audit] Owner user not yet provisioned. Skipping re-linking and recovery.");
            return;
        }

        log.info("👤 [Audit] Current Owner ID: {}", owner.getId());

        // Identifying orphaned posts
        List<Post> allPosts = postRepository.findAll();
        
        // Ownership analysis
        java.util.Map<Long, Long> ownershipMap = allPosts.stream()
                .filter(p -> p.getUser() != null)
                .collect(java.util.stream.Collectors.groupingBy(p -> p.getUser().getId(), java.util.stream.Collectors.counting()));
        
        log.info("📊 [Audit] Post Ownership Distribution (User ID -> Count): {}", ownershipMap);
        
        for (Long uid : ownershipMap.keySet()) {
            userRepository.findById(uid).ifPresent(u -> 
                log.info("👤 [Audit] User ID {} (Email: {}) owns {} posts", uid, u.getEmail(), ownershipMap.get(uid))
            );
        }

        long orphanedCount = allPosts.stream()
                .filter(p -> p.getUser() == null || !userRepository.existsById(p.getUser().getId()))
                .count();

        log.info("👻 [Audit] Orphaned Posts Found: {}", orphanedCount);

        if (orphanedCount > 0) {
            log.info("🛠️ [Audit] Re-linking orphaned posts to Owner account (ID: {})...", owner.getId());
            for (Post post : allPosts) {
                if (post.getUser() == null || !userRepository.existsById(post.getUser().getId())) {
                    post.setUser(owner);
                    postRepository.save(post);
                }
            }
            log.info("✅ [Audit] Re-linking complete. {} records recovered.", orphanedCount);
        } else {
            log.info("✅ [Audit] No orphaned records detected.");
        }
    }
}
