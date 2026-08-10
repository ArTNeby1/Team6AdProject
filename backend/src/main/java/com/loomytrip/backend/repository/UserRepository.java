package com.loomytrip.backend.repository;

import com.loomytrip.backend.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
