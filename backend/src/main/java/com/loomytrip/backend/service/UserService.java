package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.request.UpdatePreferencesRequest;
import com.loomytrip.backend.dto.request.UpdateProfileRequest;
import com.loomytrip.backend.dto.response.UserProfileResponse;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        return toProfile(currentUser());
    }

    /**
     * Was previously a Frontend_Web-only operation — ProfilePage.jsx's "Save Preferences"
     * only ever touched AuthContext's local state / localStorage, never the backend, so it
     * never survived a re-login on another device or a cleared localStorage. `users` had no
     * columns for this until V4__add_users_preferences.sql.
     */
    @Transactional
    public UserProfileResponse updatePreferences(UpdatePreferencesRequest request) {
        User user = currentUser();
        if (request.travelStyle() != null) {
            user.setTravelStyle(request.travelStyle().isBlank() ? null : request.travelStyle());
        }
        if (request.preferTransport() != null) {
            user.setPreferTransport(request.preferTransport().isBlank() ? null : request.preferTransport());
        }
        return toProfile(userRepository.save(user));
    }

    /**
     * Editable identity fields (username/age/gender) — distinct from
     * {@link #updatePreferences} which only ever touched travel_style/prefer_transport.
     * Email and password are intentionally untouched here: no verification flow exists yet
     * to safely change either.
     */
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = currentUser();
        if (request.username() != null) {
            String trimmed = request.username().trim();
            if (trimmed.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME", "Username cannot be blank");
            }
            user.setUsername(trimmed);
        }
        if (request.age() != null) {
            if (request.age() < 0 || request.age() > 150) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AGE", "Age must be between 0 and 150");
            }
            user.setAge(request.age());
        }
        if (request.gender() != null) {
            user.setGender(request.gender().isBlank() ? null : request.gender().trim());
        }
        return toProfile(userRepository.save(user));
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAge(),
                user.getGender(),
                user.getTravelStyle(),
                user.getPreferTransport()
        );
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }
}
