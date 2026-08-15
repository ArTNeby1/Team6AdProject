package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.response.NotificationResponse;
import com.loomytrip.backend.service.NotificationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notificationService.listMine(unreadOnly);
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notificationService.markAllRead();
    }
}
