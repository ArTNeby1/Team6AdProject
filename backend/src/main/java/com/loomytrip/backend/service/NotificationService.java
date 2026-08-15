package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.response.NotificationResponse;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.entity.UserNotification;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.repository.UserNotificationRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final UserNotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(UserNotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(boolean unreadOnly) {
        User user = currentUser();
        List<UserNotification> notifications = unreadOnly
                ? notificationRepository.findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(user.getId())
                : notificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        return notifications.stream().map(this::toResponse).toList();
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        UserNotification notification = notificationRepository.findByIdAndUser_Id(id, currentUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead() {
        Instant now = Instant.now();
        notificationRepository.findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(currentUser().getId())
                .forEach(notification -> notification.setReadAt(now));
    }

    @Transactional
    public void createImportNotification(PlanningSession session, boolean successful, String detail) {
        UserNotification notification = new UserNotification();
        notification.setUser(session.getUser());
        notification.setType(successful ? "IMPORT_COMPLETE" : "IMPORT_FAILED");
        notification.setTitle(successful ? "Your itinerary import is ready" : "Your itinerary import needs attention");
        notification.setBody(successful
                ? "We extracted places from " + safeTitle(session) + ". Review and confirm your itinerary."
                : detail);
        notification.setPlanningSession(session);
        notificationRepository.save(notification);
    }

    private static String safeTitle(PlanningSession session) {
        return session.getTitle() == null || session.getTitle().isBlank() ? "your travel notes" : session.getTitle();
    }

    private NotificationResponse toResponse(UserNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getPlanningSession() == null ? null : notification.getPlanningSession().getId(),
                notification.getTrip() == null ? null : notification.getTrip().getId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }
}
