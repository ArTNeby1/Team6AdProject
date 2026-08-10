package com.loomytrip.backend.security;

import com.loomytrip.backend.repository.AdminRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads back-office operators from the {@code admin} table and maps their
 * {@code AdminRole} onto Spring Security authorities:
 * {@code admin -> ROLE_ADMIN}, {@code super_admin -> ROLE_SUPER_ADMIN}.
 * <p>
 * Kept separate from {@link CustomUserDetailsService} on purpose: admins and
 * travelers are different principals in different tables, and role authorities
 * are resolved from the database on every request so a role change takes effect
 * immediately without re-issuing the token.
 */
@Service
public class AdminDetailsService {

    private final AdminRepository adminRepository;

    public AdminDetailsService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public UserDetails loadAdminByEmail(String email) {
        var admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found: " + email));
        return User.withUsername(admin.getEmail())
                .password(admin.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name().toUpperCase()))
                .build();
    }
}
