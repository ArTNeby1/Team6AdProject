package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByDestination_IdOrderByCreatedAtDesc(Long destinationId);
}
