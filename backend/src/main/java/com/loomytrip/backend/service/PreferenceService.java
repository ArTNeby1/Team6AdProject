package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.request.UpsertPreferenceRequest;
import com.loomytrip.backend.dto.response.PreferenceResponse;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.entity.UserPreference;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.UserPreferenceRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;

    public PreferenceService(
            UserPreferenceRepository userPreferenceRepository,
            UserRepository userRepository,
            EntityMapper entityMapper
    ) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userRepository = userRepository;
        this.entityMapper = entityMapper;
    }

    @Transactional(readOnly = true)
    public List<PreferenceResponse> listMyPreferences() {
        User user = currentUser();
        return userPreferenceRepository.findByUser_Id(user.getId()).stream()
                .map(entityMapper::toPreference)
                .toList();
    }

    @Transactional
    public PreferenceResponse upsert(UpsertPreferenceRequest request) {
        User user = currentUser();
        UserPreference preference = userPreferenceRepository
                .findByUser_IdAndPreferenceKey(user.getId(), request.preferenceKey())
                .orElseGet(UserPreference::new);

        preference.setUser(user);
        preference.setPreferenceKey(request.preferenceKey());
        preference.setPreferenceValue(request.preferenceValue());
        return entityMapper.toPreference(userPreferenceRepository.save(preference));
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }
}
