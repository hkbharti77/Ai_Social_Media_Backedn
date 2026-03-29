package com.aiplatform.repository;

import com.aiplatform.model.MicrositeLink;
import com.aiplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MicrositeLinkRepository extends JpaRepository<MicrositeLink, Long> {
    List<MicrositeLink> findByUserOrderBySortOrderAsc(User user);
    
    @Modifying
    @Query("UPDATE MicrositeLink m SET m.clickCount = m.clickCount + 1 WHERE m.id = :id")
    void incrementClickCount(Long id);
}
