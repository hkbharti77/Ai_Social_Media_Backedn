package com.aiplatform.repository;

import com.aiplatform.model.SocialAccount;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    List<SocialAccount> findByUser(User user);
    List<SocialAccount> findByPlatform(String platform);

    // Used during OAuth callback to detect and replace existing accounts
    Optional<SocialAccount> findByUserAndPlatformAndPageId(User user, String platform, String pageId);
    Optional<SocialAccount> findByUserAndPlatformAndIgBusinessAccountId(User user, String platform, String igBusinessAccountId);
    List<SocialAccount> findByUserAndPlatform(User user, String platform);
}
