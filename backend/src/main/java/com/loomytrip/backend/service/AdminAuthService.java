package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.request.AdminLoginRequest;
import com.loomytrip.backend.dto.response.AdminAuthResponse;
import com.loomytrip.backend.entity.Admin;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.repository.AdminRepository;
import com.loomytrip.backend.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Authenticates back-office operators against the {@code admin} table.
 * <p>
 * Unlike {@link AuthService}, this does not go through the shared
 * {@code AuthenticationManager}: that manager is wired to the traveler
 * {@code users} table, so admin credentials are verified directly here. A wrong
 * email and a wrong password return the same generic error to avoid leaking
 * which admin accounts exist.
 */
@Service
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthService(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AdminAuthResponse login(AdminLoginRequest request) {
        String email = request.email().trim().toLowerCase();
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw invalidCredentials();
        }

        String token = jwtService.generateAdminToken(admin.getId(), admin.getEmail());
        return AdminAuthResponse.bearer(token, admin.getId(), admin.getEmail(), admin.getRole());
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }
}
