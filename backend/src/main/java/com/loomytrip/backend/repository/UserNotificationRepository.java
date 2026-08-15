package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.UserNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<UserNotification> findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<UserNotification> findByIdAndUser_Id(Long id, Long userId);
}
