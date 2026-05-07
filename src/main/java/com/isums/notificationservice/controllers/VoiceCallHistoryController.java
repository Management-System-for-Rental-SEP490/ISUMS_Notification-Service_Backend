package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.domains.dtos.VoiceCallDto;
import com.isums.notificationservice.domains.entities.VoiceCallJob;
import com.isums.notificationservice.infrastructures.repositories.VoiceCallJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/calls")
@RequiredArgsConstructor
public class VoiceCallHistoryController {

    private final VoiceCallJobRepository voiceJobRepo;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<VoiceCallDto>>> listMyCalls(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Page<VoiceCallJob> jobs = voiceJobRepo.findAllByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        Page<VoiceCallDto> mapped = jobs.map(this::toDto);
        return ResponseEntity.ok(ApiResponses.ok(mapped, "OK"));
    }

    private VoiceCallDto toDto(VoiceCallJob j) {
        return new VoiceCallDto(
                j.getId(), j.getUserId(), j.getAlertId(), j.getEventType(),
                j.getLocale(), j.getStatus(), j.getDtmfReceived(),
                j.getAcknowledgedAt(), j.getAttemptNumber(), j.getMaxAttempts(),
                j.getDurationSec(), j.getCostVnd(), j.getCreatedAt());
    }
}
