package com.isums.notificationservice.domains.entities;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceGender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voice_audio_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceAudioCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 128)
    private String cacheKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocaleType locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "voice_gender", nullable = false, length = 10)
    private VoiceGender voiceGender;

    @Column(name = "voice_speed", nullable = false, precision = 3, scale = 2)
    private BigDecimal voiceSpeed;

    @Column(name = "rendered_text", nullable = false, columnDefinition = "text")
    private String renderedText;

    @Column(name = "s3_bucket", nullable = false, length = 200)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, length = 400)
    private String s3Key;

    @Column(name = "public_url", nullable = false, columnDefinition = "text")
    private String publicUrl;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "bytes")
    private Integer bytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    @Builder.Default
    private Instant lastUsedAt = Instant.now();

    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private int hitCount = 0;
}
