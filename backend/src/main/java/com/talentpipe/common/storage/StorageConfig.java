package com.talentpipe.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Registers exactly one {@link FileStorageService} bean based on
 * {@code talentpipe.storage.provider} (env var: {@code STORAGE_PROVIDER}).
 *
 * <ul>
 *   <li>{@code local} (default) — {@link LocalFileStorageService}</li>
 *   <li>{@code s3} — {@link S3FileStorageService} + an AWS {@link S3Client}</li>
 * </ul>
 *
 * <p>Switching between providers requires only an env var change — no code
 * changes in controllers or services.</p>
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    /**
     * Local filesystem storage — active when {@code STORAGE_PROVIDER} is absent
     * or set to {@code local}.
     */
    @Bean
    @ConditionalOnProperty(
            name = "talentpipe.storage.provider",
            havingValue = "local",
            matchIfMissing = true)
    public FileStorageService localFileStorageService(StorageProperties properties) {
        return new LocalFileStorageService(properties);
    }

    /**
     * S3 client — created only when the S3 provider is active, so tests and
     * local dev never need AWS credentials on the classpath.
     */
    @Bean
    @ConditionalOnProperty(name = "talentpipe.storage.provider", havingValue = "s3")
    public S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.s3Region()))
                .build();
    }

    /**
     * S3 storage — active when {@code STORAGE_PROVIDER=s3}.
     */
    @Bean
    @ConditionalOnProperty(name = "talentpipe.storage.provider", havingValue = "s3")
    public FileStorageService s3FileStorageService(S3Client s3Client,
                                                   StorageProperties properties) {
        return new S3FileStorageService(s3Client, properties);
    }
}
