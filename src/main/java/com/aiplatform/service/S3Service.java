package com.aiplatform.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private static final String S3_FOLDER = "social_media/";

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Lists all files for a specific user in the social_media/{userId} folder.
     */
    public List<Map<String, String>> listAllFiles(Long userId) {
        String userPrefix = S3_FOLDER + userId + "/";
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(userPrefix)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);

        return response.contents().stream()
                .filter(obj -> obj.size() > 0) // Skip folder placeholders
                .map(obj -> Map.of(
                    "url", getObjectUrl(obj.key()),
                    "downloadUrl", generateDownloadUrl(obj.key())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Uploads a MultipartFile to S3 in social_media/{userId} folder and returns a pre-signed URL.
     */
    public String uploadFile(MultipartFile file, Long userId) throws IOException {
        String sanitizedOriginalName = sanitizeFilename(file.getOriginalFilename());
        String key = S3_FOLDER + userId + "/" + UUID.randomUUID().toString() + "_" + sanitizedOriginalName;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return getObjectUrl(key);
    }

    /**
     * Uploads an InputStream to S3 with a specific filename in social_media/{userId} folder.
     * Returns a pre-signed URL for immediate access.
     */
    public String uploadFile(String fileName, InputStream inputStream, Long userId) {
        String key = S3_FOLDER + userId + "/" + fileName;

        try {
            byte[] bytes = inputStream.readAllBytes();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("image/png")
                    .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream for S3 upload", e);
        }
        return getObjectUrl(key);
    }

    /**
     * Constructs a permanent S3 object URL.
     * Format: https://[bucket].s3.[region].amazonaws.com/[key]
     */
    public String getObjectUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }

    /**
     * Extracts the S3 Key from a pre-signed (possibly defunct) URL and returns the permanent object URL.
     */
    public String getPermanentUrlFromPresigned(String presignedUrl) {
        if (presignedUrl == null || !presignedUrl.contains(".amazonaws.com/")) {
            return presignedUrl;
        }
        // Extract the part before the query parameters
        return presignedUrl.split("\\?")[0];
    }

    /**
     * Extracts the S3 Key from a URL (permanent or pre-signed).
     */
    public String extractKeyFromUrl(String url) {
        if (url == null || !url.contains(".amazonaws.com/")) {
            return url;
        }
        // Extract key from the URL path: HKHBARTI77.s3.eu-north-1.amazonaws.com/[key]
        String path = url.split("\\.amazonaws\\.com/")[1].split("\\?")[0];
        return path;
    }

    /**
     * Generates a pre-signed URL that forces a download (Content-Disposition: attachment).
     */
    public String generateDownloadUrl(String key) {
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .responseContentDisposition("attachment; filename=\"" + fileName + "\"")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1)) // Short-lived for downloads
                .getObjectRequest(getRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    /**
     * Deletes a file from S3 bucket given its key.
     */
    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    /**
     * Sanitize filenames to prevent path traversal or S3 key manipulation.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        // Remove all characters except alphanumeric, dots, dashes, and underscores
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
