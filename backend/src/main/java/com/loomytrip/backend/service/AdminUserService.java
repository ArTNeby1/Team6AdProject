package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.response.AdminUserResponse;
import com.loomytrip.backend.dto.response.PageResponse;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only queries over traveler accounts for the admin console.
 * No mutation methods live here by design — the S1 user list is view-only.
 */
@Service
public class AdminUserService {

    /** Hard cap so a client can't request an unbounded page. */
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(int page, int size, String query) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id"));

        String q = query == null ? "" : query.trim();
        Page<User> result = q.isEmpty()
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCase(q, pageable);

        List<AdminUserResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.from(result, content);
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAge(),
                user.getGender(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
