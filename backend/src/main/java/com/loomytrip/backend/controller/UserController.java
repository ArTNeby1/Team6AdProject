package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.request.UpdatePreferencesRequest;
import com.loomytrip.backend.dto.request.UpdateProfileRequest;
import com.loomytrip.backend.dto.response.UserProfileResponse;
import com.loomytrip.backend.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserProfileResponse getMyProfile() {
        return userService.getMyProfile();
    }

    @PutMapping("/preferences")
    public UserProfileResponse updatePreferences(@RequestBody UpdatePreferencesRequest request) {
        return userService.updatePreferences(request);
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(@RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(request);
    }
}
