package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.ImportedSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportedSourceRepository extends JpaRepository<ImportedSource, Long> {
    List<ImportedSource> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
