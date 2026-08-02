package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.ExtractedActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedActivityRepository extends JpaRepository<ExtractedActivity, Long> {
    List<ExtractedActivity> findByImportedSource_Id(Long importId);
}
