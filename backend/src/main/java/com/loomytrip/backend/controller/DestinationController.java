package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.service.DestinationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    @GetMapping
    public List<DestinationResponse> search(@RequestParam(required = false) String q) {
        return destinationService.search(q);
    }
}
