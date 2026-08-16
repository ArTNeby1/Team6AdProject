package com.loomytrip.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.loomytrip.backend.config.JwtProperties;
import com.loomytrip.backend.entity.Admin;
import com.loomytrip.backend.entity.AdminRole;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.entity.UserRole;
import com.loomytrip.backend.repository.AdminRepository;
import com.loomytrip.backend.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class JwtAndDetailsServiceTest {

    private static final String SECRET = "a-32-character-minimum-secret-key!";

    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;

    @Test
    void jwtService_roundTripsTravelerAndAdminClaims() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60_000));

        String traveler = service.generateToken(7L, "traveler@example.com");
        String admin = service.generateAdminToken(8L, "admin@example.com");

        assertThat(service.isTokenValid(traveler)).isTrue();
        assertThat(service.extractUserId(traveler)).isEqualTo(7L);
        assertThat(service.extractEmail(traveler)).isEqualTo("traveler@example.com");
        assertThat(service.extractType(traveler)).isEqualTo(PrincipalType.USER);
        assertThat(service.extractType(admin)).isEqualTo(PrincipalType.ADMIN);
    }

    @Test
    void jwtService_rejectsExpiredAndWronglySignedTokens() {
        JwtService expiredService = new JwtService(new JwtProperties(SECRET, -1));
        String expired = expiredService.generateToken(1L, "expired@example.com");
        JwtService otherKeyService = new JwtService(new JwtProperties(
                "a-different-32-character-secret-key!", 60_000));

        assertThat(expiredService.isTokenValid(expired)).isFalse();
        assertThat(otherKeyService.isTokenValid(expired)).isFalse();
    }

    @Test
    void userAndAdminDetailsServices_mapRolesAndRejectUnknownAccounts() {
        User user = new User();
        user.setEmail("traveler@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.traveler);
        when(userRepository.findByEmail("traveler@example.com")).thenReturn(Optional.of(user));

        var travelerDetails = new CustomUserDetailsService(userRepository)
                .loadUserByUsername("traveler@example.com");
        assertThat(travelerDetails.getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_TRAVELER");

        Admin admin = new Admin();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.setRole(AdminRole.super_admin);
        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        var adminDetails = new AdminDetailsService(adminRepository).loadAdminByEmail("admin@example.com");
        assertThat(adminDetails.getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_SUPER_ADMIN");

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new CustomUserDetailsService(userRepository)
                .loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
