package com.revo.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URI;

@Configuration
public class R2Config {

    @Bean(destroyMethod = "close")
    public S3Client r2Client(
            @Value("${app.r2.endpoint}") String endpoint,
            @Value("${app.r2.access-key-id}") String accessKeyId,
            @Value("${app.r2.secret-access-key}") String secretAccessKey
    ) {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(
                        accessKeyId,
                        secretAccessKey
                );

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(credentials)
                )
                .serviceConfiguration(s3Config)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner r2Presigner(
            @Value("${app.r2.endpoint}") String endpoint,
            @Value("${app.r2.access-key-id}") String accessKeyId,
            @Value("${app.r2.secret-access-key}") String secretAccessKey
    ) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        accessKeyId,
                                        secretAccessKey
                                )
                        )
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }
}