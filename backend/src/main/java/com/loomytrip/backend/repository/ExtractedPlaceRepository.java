package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.ExtractedPlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedPlaceRepository extends JpaRepository<ExtractedPlace, Long> {
    List<ExtractedPlace> findByImportedSource_Id(Long importId);
}
