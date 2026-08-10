package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.request.UpdatePreferencesRequest;
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
