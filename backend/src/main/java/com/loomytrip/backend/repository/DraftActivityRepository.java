package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.DraftActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftActivityRepository extends JpaRepository<DraftActivity, Long> {
    List<DraftActivity> findBySession_Id(Long sessionId);

    void deleteBySession_Id(Long sessionId);
}
