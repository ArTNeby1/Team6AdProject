package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.UserPreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    List<UserPreference> findByUser_Id(Long userId);
    Optional<UserPreference> findByUser_IdAndPreferenceKey(Long userId, String preferenceKey);
}
