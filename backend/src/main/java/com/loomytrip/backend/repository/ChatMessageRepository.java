package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySession_IdOrderByCreatedAtAsc(Long sessionId);
}
