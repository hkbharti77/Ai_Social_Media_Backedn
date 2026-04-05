package com.aiplatform.service;

import com.aiplatform.dto.AdminAnalyticsDtos.*;
import com.aiplatform.model.AiUsageLog;
import com.aiplatform.model.User;
import com.aiplatform.repository.AiUsageLogRepository;
import com.aiplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final AiUsageLogRepository aiUsageLogRepository;

    public AdminDashboardStats getOverviewStats() {
        Long totalUsers = userRepository.count();
        Long activeUsersToday = userRepository.countByLastLoginAtAfter(LocalDateTime.now().minusDays(1));
        Long activeUsersThisWeek = userRepository.countByLastLoginAtAfter(LocalDateTime.now().minusDays(7));
        
        // Sum total tokens from AiUsageLog
        Long totalTokens = aiUsageLogRepository.sumTotalTokens();
        
        return AdminDashboardStats.builder()
                .totalUsers(totalUsers)
                .activeUsersToday(activeUsersToday)
                .activeUsersThisWeek(activeUsersThisWeek)
                .totalTokensUsed(totalTokens != null ? totalTokens : 0L)
                .totalCreditsSpent(0.0) // Mock cost if needed
                .build();
    }

    public List<ModelUsageStats> getModelDetailedStats() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        List<Map<String, Object>> rawStats = aiUsageLogRepository.getModelUsageStats(startDate);
        
        return rawStats.stream().map(map -> ModelUsageStats.builder()
                .modelId((String) map.get("modelId"))
                .totalTokens((Long) map.get("totalTokens"))
                .promptTokens((Long) map.get("promptTokens"))
                .completionTokens((Long) map.get("completionTokens"))
                .callCount((Long) map.get("callCount"))
                .build())
                .collect(Collectors.toList());
    }

    public List<UserIntelligenceDto> getUserIntelligence() {
        List<Map<String, Object>> topUsers = aiUsageLogRepository.findTopTokenUsers();
        if (topUsers.isEmpty()) return new ArrayList<>();

        List<Long> userIds = topUsers.stream()
                .map(map -> (Long) map.get("userId"))
                .collect(Collectors.toList());

        List<Map<String, Object>> modelUsage = aiUsageLogRepository.findModelUsageByUsers(userIds);
        
        // Group model usage by user
        Map<Long, List<Map<String, Object>>> userModelMap = modelUsage.stream()
                .collect(Collectors.groupingBy(map -> (Long) map.get("userId")));

        return topUsers.stream().limit(15).map(map -> {
            Long userId = (Long) map.get("userId");
            List<Map<String, Object>> models = userModelMap.getOrDefault(userId, new ArrayList<>());
            
            Map<String, Long> breakdown = models.stream()
                    .collect(Collectors.toMap(
                            m -> (String) m.get("modelId"),
                            m -> (Long) m.get("totalTokens")
                    ));

            String topModel = models.stream()
                    .max((m1, m2) -> ((Long) m1.get("totalTokens")).compareTo((Long) m2.get("totalTokens")))
                    .map(m -> (String) m.get("modelId"))
                    .orElse("N/A");

            return UserIntelligenceDto.builder()
                    .userId(userId)
                    .email((String) map.get("email"))
                    .fullName((String) map.get("fullName"))
                    .totalTokens((Long) map.get("totalTokens"))
                    .loginCount((Long) map.get("loginCount"))
                    .lastLoginAt(map.get("lastLoginAt") != null ? ((LocalDateTime) map.get("lastLoginAt")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "Never")
                    .topModelId(topModel)
                    .modelBreakdown(breakdown)
                    .build();
        }).collect(Collectors.toList());
    }

    public Page<UserIntelligenceDto> getUserDirectoryIntelligence(String query, Pageable pageable) {
        Page<User> usersPage = userRepository.searchUsers(query, pageable);
        if (usersPage.isEmpty()) return Page.empty();

        List<Long> userIds = usersPage.getContent().stream().map(User::getId).collect(Collectors.toList());
        List<Map<String, Object>> modelUsage = aiUsageLogRepository.findModelUsageByUsers(userIds);
        
        Map<Long, List<Map<String, Object>>> userModelMap = modelUsage.stream()
                .collect(Collectors.groupingBy(map -> (Long) map.get("userId")));

        return usersPage.map(u -> {
            List<Map<String, Object>> models = userModelMap.getOrDefault(u.getId(), new ArrayList<>());
            
            Map<String, Long> breakdown = models.stream()
                    .collect(Collectors.toMap(
                            m -> (String) m.get("modelId"),
                            m -> (Long) m.get("totalTokens")
                    ));

            String topModel = models.stream()
                    .max((m1, m2) -> ((Long) m1.get("totalTokens")).compareTo((Long) m2.get("totalTokens")))
                    .map(m -> (String) m.get("modelId"))
                    .orElse("New User");

            Long totalTokens = models.stream().mapToLong(m -> (Long) m.get("totalTokens")).sum();

            return UserIntelligenceDto.builder()
                    .userId(u.getId())
                    .email(u.getEmail())
                    .fullName(u.getFullName())
                    .totalTokens(totalTokens)
                    .loginCount(u.getLoginCount() != null ? u.getLoginCount() : 0L)
                    .lastLoginAt(u.getLastLoginAt() != null ? u.getLastLoginAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "Never")
                    .topModelId(topModel)
                    .modelBreakdown(breakdown)
                    .build();
        });
    }

    public Page<AiUsageLog> getAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return aiUsageLogRepository.findAll(pageable);
    }
}
