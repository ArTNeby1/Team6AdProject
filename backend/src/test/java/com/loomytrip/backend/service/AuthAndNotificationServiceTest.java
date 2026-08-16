package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loomytrip.backend.dto.request.AdminLoginRequest;
import com.loomytrip.backend.dto.request.LoginRequest;
import com.loomytrip.backend.dto.request.RegisterRequest;
import com.loomytrip.backend.dto.request.UpdatePreferencesRequest;
import com.loomytrip.backend.dto.response.AdminAuthResponse;
import com.loomytrip.backend.dto.response.AuthResponse;
import com.loomytrip.backend.dto.response.NotificationResponse;
import com.loomytrip.backend.dto.response.UserProfileResponse;
import com.loomytrip.backend.entity.Admin;
import com.loomytrip.backend.entity.AdminRole;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.entity.UserNotification;
import com.loomytrip.backend.entity.UserRole;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.repository.AdminRepository;
import com.loomytrip.backend.repository.UserNotificationRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.security.JwtService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthAndNotificationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private AdminRepository adminRepository;
    @Mock private UserNotificationRepository notificationRepository;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "alice", "a@example.com", "password1", 20, "F")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("EMAIL_EXISTS");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_encodesPassword_andReturnsBearerToken() {
        when(userRepository.existsByEmail("A@Example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(7L);
            return user;
        });
        when(jwtService.generateToken(7L, "a@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.register(new RegisterRequest(
                null, "A@Example.com", "secret123", 22, "M"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("a@example.com");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("a");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.traveler);
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_mapsBadCredentialsToApiException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@example.com", "x")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_returnsTokenForValidUser() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("a@example.com", "x"));
        User user = new User();
        user.setId(3L);
        user.setEmail("a@example.com");
        user.setUsername("alice");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(3L, "a@example.com")).thenReturn("tok");

        AuthResponse response = authService.login(new LoginRequest("A@Example.com", "secret"));

        assertThat(response.accessToken()).isEqualTo("tok");
        assertThat(response.userId()).isEqualTo(3L);
    }

    @Test
    void adminLogin_rejectsUnknownEmail_andWrongPassword() {
        AdminAuthService adminAuthService = new AdminAuthService(adminRepository, passwordEncoder, jwtService);
        when(adminRepository.findByEmail("admin@loomytrip.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuthService.login(
                new AdminLoginRequest("admin@loomytrip.local", "x")))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_CREDENTIALS");

        Admin admin = new Admin();
        admin.setId(1L);
        admin.setEmail("admin@loomytrip.local");
        admin.setPasswordHash("hash");
        admin.setRole(AdminRole.admin);
        when(adminRepository.findByEmail("admin@loomytrip.local")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(
                new AdminLoginRequest("admin@loomytrip.local", "wrong")))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void adminLogin_returnsAdminBearerToken() {
        AdminAuthService adminAuthService = new AdminAuthService(adminRepository, passwordEncoder, jwtService);
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setEmail("admin@loomytrip.local");
        admin.setPasswordHash("hash");
        admin.setRole(AdminRole.super_admin);
        when(adminRepository.findByEmail("admin@loomytrip.local")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("ok", "hash")).thenReturn(true);
        when(jwtService.generateAdminToken(1L, "admin@loomytrip.local")).thenReturn("admin-jwt");

        AdminAuthResponse response = adminAuthService.login(
                new AdminLoginRequest("Admin@Loomytrip.local", "ok"));

        assertThat(response.accessToken()).isEqualTo("admin-jwt");
        assertThat(response.role()).isEqualTo(AdminRole.super_admin);
    }

    @Test
    void userService_updatesPreferences_andBlankClears() {
        UserService userService = new UserService(userRepository);
        User user = new User();
        user.setId(1L);
        user.setEmail("a@example.com");
        user.setUsername("alice");
        user.setTravelStyle("culture");
        authenticate("a@example.com");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse updated = userService.updatePreferences(
                new UpdatePreferencesRequest("  ", "transit"));

        assertThat(updated.travelStyle()).isNull();
        assertThat(updated.preferTransport()).isEqualTo("transit");
        assertThat(userService.getMyProfile().email()).isEqualTo("a@example.com");
    }

    @Test
    void notificationService_listsUnread_marksRead_andCreatesImportNotice() {
        NotificationService notificationService =
                new NotificationService(notificationRepository, userRepository);
        User user = new User();
        user.setId(1L);
        user.setEmail("a@example.com");
        authenticate("a@example.com");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));

        UserNotification unread = new UserNotification();
        unread.setId(11L);
        unread.setType("IMPORT_COMPLETE");
        unread.setTitle("ready");
        unread.setBody("body");
        unread.setUser(user);
        when(notificationRepository.findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(unread));
        when(notificationRepository.findByIdAndUser_Id(11L, 1L)).thenReturn(Optional.of(unread));

        List<NotificationResponse> listed = notificationService.listMine(true);
        assertThat(listed).hasSize(1);

        NotificationResponse marked = notificationService.markRead(11L);
        assertThat(marked.readAt()).isNotNull();

        notificationService.markAllRead();
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce())
                .findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(1L);

        PlanningSession session = new PlanningSession();
        session.setId(5L);
        session.setUser(user);
        session.setTitle("SG notes");
        notificationService.createImportNotification(session, true, null);

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("IMPORT_COMPLETE");
        assertThat(captor.getValue().getBody()).contains("SG notes");
    }

    @Test
    void notificationService_rejectsForeignOrMissingNotification_andBuildsFailureNotice() {
        NotificationService notificationService =
                new NotificationService(notificationRepository, userRepository);
        User user = new User();
        user.setId(1L);
        user.setEmail("a@example.com");
        authenticate("a@example.com");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(notificationRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(99L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("NOTIFICATION_NOT_FOUND");

        PlanningSession session = new PlanningSession();
        session.setId(6L);
        session.setUser(user);
        session.setTitle(" ");
        notificationService.createImportNotification(session, false, "Please revise your notes.");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("IMPORT_FAILED");
        assertThat(captor.getValue().getTitle()).isEqualTo("Your itinerary import needs attention");
        assertThat(captor.getValue().getBody()).isEqualTo("Please revise your notes.");
    }

    private static void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of())
        );
    }
}
