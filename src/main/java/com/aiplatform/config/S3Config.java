package com.aiplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    @Bean
    public S3Client s3Client() {
        String cleanAccessKey = accessKey != null ? accessKey.trim() : "";
        String cleanSecretKey = secretKey != null ? secretKey.trim() : "";
        String cleanRegion = region != null ? region.trim() : "ap-south-1";

        return S3Client.builder()
                .region(Region.of(cleanRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cleanAccessKey, cleanSecretKey)))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        String cleanAccessKey = accessKey != null ? accessKey.trim() : "";
        String cleanSecretKey = secretKey != null ? secretKey.trim() : "";
        String cleanRegion = region != null ? region.trim() : "ap-south-1";

        return S3Presigner.builder()
                .region(Region.of(cleanRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cleanAccessKey, cleanSecretKey)))
                .build();
    }
}
