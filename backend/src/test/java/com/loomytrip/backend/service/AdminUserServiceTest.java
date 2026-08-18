package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.loomytrip.backend.dto.response.AdminUserResponse;
import com.loomytrip.backend.dto.response.PageResponse;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.entity.UserRole;
import com.loomytrip.backend.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Pure unit tests for {@link AdminUserService}: the repository is mocked, so
 * these assert the service's own logic (page/size clamping, the blank-vs-search
 * query branch, and entity -> DTO mapping) without a Spring context or a DB.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserService service;

    private User sampleUser() {
        User u = new User();
        u.setId(42L);
        u.setUsername("Ross Geller");
        u.setEmail("ross@friends.tv");
        u.setAge(30);
        u.setGender("Male");
        u.setPasswordHash("$2b$10$secrethash");
        u.setRole(UserRole.traveler);
        u.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        return u;
    }

    @Test
    void blankQuery_usesFindAll_andMapsFields() {
        Page<User> page = new PageImpl<>(List.of(sampleUser()), PageRequest.of(0, 20), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<AdminUserResponse> result = service.listUsers(0, 20, "  ");

        // blank/whitespace query must not hit the search method
        verify(userRepository).findAll(any(Pageable.class));
        verifyNoMoreInteractions(userRepository);

        assertThat(result.totalElements()).isEqualTo(1);
        AdminUserResponse dto = result.content().get(0);
        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.email()).isEqualTo("ross@friends.tv");
        assertThat(dto.role()).isEqualTo(UserRole.traveler);
        // DTO must never expose the password hash (it isn't even a field)
        assertThat(dto.toString()).doesNotContain("secrethash");
    }

    @Test
    void nonBlankQuery_usesSearch_withTrimmedTerm() {
        Page<User> page = new PageImpl<>(List.of(sampleUser()), PageRequest.of(0, 20), 1);
        when(userRepository.findByEmailContainingIgnoreCase(eq("friends"), any(Pageable.class)))
                .thenReturn(page);

        service.listUsers(0, 20, "  friends  ");

        verify(userRepository).findByEmailContainingIgnoreCase(eq("friends"), any(Pageable.class));
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void size_isClampedTo100_andNegativePageTo0() {
        Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        service.listUsers(-5, 999, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void size_belowOne_isClampedToOne() {
        Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 1), 0);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        service.listUsers(0, 0, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }
}
