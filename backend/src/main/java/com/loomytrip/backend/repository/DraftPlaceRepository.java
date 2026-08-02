package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.DraftPlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftPlaceRepository extends JpaRepository<DraftPlace, Long> {
    List<DraftPlace> findBySession_Id(Long sessionId);
}
