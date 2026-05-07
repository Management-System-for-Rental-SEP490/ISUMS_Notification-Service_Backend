package com.isums.notificationservice.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.pinpointsmsvoicev2.PinpointSmsVoiceV2Client;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;

import java.time.Duration;

/**
 * Beans for the voice-notification stack: Stringee RestClient (voice TTS)
 * and AWS clients (SNS for SMS, Polly + S3 for pre-synth audio).
 *
 * <p>All values default to safe dev-only values so the service boots in a
 * clean checkout — real credentials go into environment variables.
 */
@Configuration
public class VoiceNotificationConfig {

    @Value("${app.notification.stringee.base-url:https://api.stringee.com}")
    private String stringeeBaseUrl;

    @Value("${app.notification.aws.region:ap-southeast-1}")
    private String awsRegion;

    @Bean
    public RestClient stringeeRestClient() {
        return RestClient.builder()
                .baseUrl(stringeeBaseUrl)
                .requestFactory(clientHttpRequestFactory())
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        return factory;
    }

    @Bean
    public PollyClient pollyClient() {
        return PollyClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public S3Client voiceAudioS3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Value("${app.notification.aws.voice.region:us-east-1}")
    private String pinpointVoiceRegion;

    @Bean
    public PinpointSmsVoiceV2Client pinpointSmsVoiceV2Client() {
        return PinpointSmsVoiceV2Client.builder()
                .region(Region.of(pinpointVoiceRegion))
                .build();
    }

    /**
     * AWS SNS client for transactional SMS. Uses the default credential
     * provider chain (env vars / instance profile / shared credentials),
     * same as Polly + S3. Region is shared with the rest of the AWS
     * stack via {@code app.notification.aws.region} (default
     * {@code ap-southeast-1}).
     *
     * <p>Activated lazily — bean is always defined but the
     * {@link com.isums.notificationservice.services.AwsSnsClient}
     * SmsProvider only routes traffic through it when
     * {@code app.notification.sms.provider=AWS_SNS}.
     */
    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
