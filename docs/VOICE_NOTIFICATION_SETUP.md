# Voice Notification — Setup & Operations

Multi-channel alert delivery with per-user preferences, subscription
tier gating, TTS voice calls in **vi / en / ja**, retry + escalation,
DTMF acknowledgement, and a DRY_RUN mode for thesis demos.

## What's new

| Area | Added |
|---|---|
| Tables | `user_notification_preferences`, `notification_subscriptions`, `channel_templates`, `channel_template_versions`, `voice_call_jobs`, `voice_call_escalations`, `voice_audio_cache` |
| Enums | `NotificationChannel`, `SubscriptionTier`, `VoiceCallStatus`, `EscalationReason`, `VoiceGender`, `AlertEventType` |
| Services | `NotificationDispatchService`, `NotificationPreferenceService`, `NotificationSubscriptionService`, `NotificationQuotaService`, `VoiceCallOrchestratorService`, `VoiceWebhookHandler`, `EscalationService`, `ChannelTemplateRenderer`, `SpeedSmsClient`, `PollyTtsSynthesizer` |
| Schedulers | `VoiceCallRetryScheduler` (每分), `MonthlyQuotaResetScheduler` (1st month 00:05 VN), `PremiumExpirationScheduler` (nightly 02:15 VN) |
| REST | `/api/notifications/preferences/me` (GET/PUT), `/subscription`, `/quota`, `/test-voice`, `/calls/me`, `/subscriptions/admin/grant-premium`, `/voice/webhook`, `/internal/dispatch` |
| Kafka | Listens `payment.subscription-activated` |
| Seeders | `VoiceAlertTemplateSeeder` seeds 8 events × (vi/en/ja) × (VOICE + SMS for 4 critical) |

## Environment variables

Required for production; all optional in DRY_RUN mode (logging only).

```bash
# Feature flag — flip to false once SpeedSMS credit is loaded
NOTIFICATION_VOICE_DRY_RUN=true

# SpeedSMS (https://speedsms.vn)
SPEEDSMS_ACCESS_TOKEN=<your-api-key>
SPEEDSMS_WEBHOOK_SECRET=<secret-for-HMAC-verify>
SPEEDSMS_BASE_URL=https://api.speedsms.vn
SPEEDSMS_VOICE_PATH=/api/voice/send
SPEEDSMS_SMS_PATH=/api/sms/send
SPEEDSMS_CALLER_ID=ISUMS

# Shared secret between Notification-Service + IoT Lambda
INTERNAL_API_KEY=<long-random-string-same-as-esp32-router>

# Public base URL the webhook will arrive on
NOTIFICATION_PUBLIC_BASE_URL=https://api-dev.isums.pro

# AWS Polly pre-synth target (only needed if non-VN users exist)
VOICE_AUDIO_BUCKET=isums-voice-tts
VOICE_AUDIO_PUBLIC_BASE=https://%s.s3.ap-southeast-1.amazonaws.com
```

## One-time setup

1. **Flyway migration auto-applies** on startup
   (`V20260425_0001__voice_notification_infra.sql`). Verify with:
   ```sql
   \dt user_notification_preferences
   \dt voice_call_jobs
   ```

2. **Template seed** runs from `VoiceAlertTemplateSeeder` on app start.
   To skip (e.g. on repeated local runs): `app.seed.voice-templates=false`.

3. **SpeedSMS account**
   - Sign up at speedsms.vn → request Voice Call API enable (they enable
     voice on request, OTP-only plan is default).
   - Copy API key → `SPEEDSMS_ACCESS_TOKEN`.
   - In their portal, set Webhook URL to
     `${NOTIFICATION_PUBLIC_BASE_URL}/api/notifications/voice/webhook`
     and generate an HMAC secret → `SPEEDSMS_WEBHOOK_SECRET`.

4. **Polly + S3** (only for ja/en users)
   - Create a bucket (suggestion: `isums-voice-tts`) with `public-read`
     default ACL.
   - The Notification-Service already has AWS credentials via
     `spring.cloud.aws.credentials.*` (used by SES today); Polly + S3
     reuse them automatically.

5. **Lambda side** — see `E:\ISUMS\tmp\lambdas\notif_dispatch_patch\`:
   - Copy `_notification_client.py` into the Lambda package.
   - Call `invoke_notification_service(...)` after each `_save_alert()`
     in `esp32-threshold-checker` and `esp32-eif-score`.
   - Set env on both Lambdas:
     `NOTIFICATION_SERVICE_URL=https://api-dev.isums.pro`
     `INTERNAL_API_KEY=<same as BE>`

6. **Asset-Service denormalisation** — for the Lambda to know which
   user to notify, `esp32_asset_map` needs a `tenantUserId` column.
   Currently the map holds only `houseId/areaId`. Patch
   `IoTDeviceServiceImpl.upsetToDynamoDB` to write `tenantUserId` at
   node assignment time (the tenant can be looked up via contract-service
   grpc: `findActiveTenantByHouseAndArea`).
   Until this patch lands, the Lambda falls back to `landlordUserId`
   on the map — voice calls go to the landlord instead of the tenant.

## Flow: what happens when GAS_CRITICAL fires

```
ESP32 → MQTT → esp32-threshold-checker Lambda
  → saves alert to esp32_alerts (DynamoDB)
  → ws-broadcaster (existing flow, in-app push)
  → POST /api/notifications/internal/dispatch (new)
       └→ NotificationDispatchService
            ├─ Email (via EmailService, template alert_gas_critical)
            ├─ Push (handled by ws-broadcaster above — skipped)
            ├─ SMS  (only if PREMIUM + smsEnabled + phone present)
            └─ Voice (only if PREMIUM + voiceEnabled + consent + !quiet_hours
                      + !rateLimit + !quotaExhausted)
                └─ VoiceCallOrchestrator
                     ├─ render voice_gas_critical template (user.locale)
                     ├─ if ja → Polly.Tomoko → S3 → audio URL
                     │  else  → SpeedSMS native TTS (Vietnamese)
                     ├─ SpeedSMS POST /voice/send
                     └─ Save voice_call_jobs row (DIALING)

SpeedSMS → dials user phone → plays TTS 2× → user presses 1 → hangs up
       → POST /api/notifications/voice/webhook {callId, status, dtmf}
            └→ VoiceWebhookHandler
                 └─ dtmf=1 → ACKNOWLEDGED, stop retries
                 └─ dtmf=2 → record escalation, dispatch to landlord
                 └─ dtmf=9 → voiceEnabled=false (opt-out)
                 └─ NO_ANSWER → schedule retry via next_retry_at
```

## REST API cheat-sheet

| Verb | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/notifications/preferences/me` | JWT | Read my preferences |
| PUT | `/api/notifications/preferences/me` | JWT | Update (all fields optional) |
| GET | `/api/notifications/preferences/me/subscription` | JWT | My tier + quota |
| GET | `/api/notifications/preferences/me/quota` | JWT | Usage + rate-limit countdown |
| POST | `/api/notifications/preferences/me/test-voice` | JWT | Fire a test GAS_CRITICAL call (1/day) |
| GET | `/api/notifications/calls/me?page&size` | JWT | My call history |
| POST | `/api/notifications/subscriptions/admin/grant-premium` | LANDLORD/SYSTEM_ADMIN | `{userId, months}` — demo shortcut |
| POST | `/api/notifications/subscriptions/admin/downgrade` | LANDLORD/SYSTEM_ADMIN | `{userId}` |
| POST | `/api/notifications/subscriptions/me/upgrade` | JWT | Returns payment intent |
| POST | `/api/notifications/internal/dispatch` | `X-Internal-Key` | From Lambda |
| POST | `/api/notifications/voice/webhook` | `X-Signature` HMAC | From SpeedSMS |

## Testing (DRY_RUN mode)

1. Start service with `NOTIFICATION_VOICE_DRY_RUN=true` (default).
2. Grant yourself PREMIUM:
   ```bash
   curl -X POST \
     http://localhost:8085/api/notifications/subscriptions/admin/grant-premium \
     -H 'Authorization: Bearer <admin-jwt>' \
     -H 'Content-Type: application/json' \
     -d '{"userId":"<your-keycloak-uuid>","months":1}'
   ```
3. Turn voice on + grant consent:
   ```bash
   curl -X PUT \
     http://localhost:8085/api/notifications/preferences/me \
     -H 'Authorization: Bearer <your-jwt>' \
     -H 'Content-Type: application/json' \
     -d '{"voiceEnabled":true,"voiceConsentGranted":true,"language":"ja_JP"}'
   ```
4. Fire test call:
   ```bash
   curl -X POST \
     http://localhost:8085/api/notifications/preferences/me/test-voice \
     -H 'Authorization: Bearer <your-jwt>'
   ```
5. Check the log — DRY_RUN prints the full rendered Japanese TTS text
   that would have been spoken:
   ```
   [SpeedSMS DRY_RUN] voice phone=+84... loop=2 text=
   緊急警報。エリアAで検出されたガス濃度が...
   ```
6. Query history:
   ```bash
   curl http://localhost:8085/api/notifications/calls/me \
     -H 'Authorization: Bearer <your-jwt>'
   ```

## Tier & quota configuration

Tier caps live in `TierQuotaPolicy.java` (not DB) so pricing tweaks
don't need a migration:

| Tier    | Voice/mo | SMS/mo | Retry max | Retry min interval |
|---------|----------|--------|-----------|---------------------|
| FREE    | 0        | 0      | 0         | 120s               |
| PREMIUM | 20       | 30     | 3         | 30s                |

At 19,000đ/month with PREMIUM × 20 voice calls × 30s average at
~800đ/minute = ~8,000đ provider cost, leaving ~11,000đ margin.

## Future work (declared, not done)

- **Brandname SMS** for Vietnamese carriers — currently SMS is gated
  behind `SPEEDSMS_ACCESS_TOKEN` but without brandname registration,
  delivery rate is poor. Use Zalo ZNS as an intermediate.
- **Asset-Service tenantUserId denorm** — see step 6 above.
- **Payment-Service VNPay/MoMo** — today `PaymentSubscriptionListener`
  waits on Kafka events that nothing publishes. Admin grant endpoint
  is the demo workaround.
- **Frontend UI** — preferences + upgrade CTA + call history view.
- **Speech-to-text ack** — more natural than DTMF but adds Polly+
  Transcribe cost.
