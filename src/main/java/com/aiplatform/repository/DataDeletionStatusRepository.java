package com.aiplatform.repository;

import com.aiplatform.model.DataDeletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataDeletionStatusRepository extends JpaRepository<DataDeletionStatus, Long> {
    Optional<DataDeletionStatus> findByConfirmationCode(String confirmationCode);
}
