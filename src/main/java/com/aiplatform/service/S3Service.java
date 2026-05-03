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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class S3Service {
    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

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
     * Returns a permanent public URL for immediate access.
     */
    public String uploadFile(String fileName, InputStream inputStream, Long userId) {
        return uploadFile(fileName, inputStream, userId, false);
    }

    /**
     * Uploads an InputStream to S3 with a specific filename in social_media/{userId} folder.
     * @param publicRead if true, sets the object ACL to public-read so the plain URL is accessible
     *                   without credentials (required for Instagram/Facebook media ingestion).
     */
    public String uploadFile(String fileName, InputStream inputStream, Long userId, boolean publicRead) {
        String key = S3_FOLDER + userId + "/" + fileName;

        try {
            byte[] bytes = inputStream.readAllBytes();

            String contentType = determineContentType(fileName);
            PutObjectRequest.Builder builder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .checksumAlgorithm(ChecksumAlgorithm.SHA256);

            if (publicRead) {
                builder.acl(ObjectCannedACL.PUBLIC_READ);
            }

            s3Client.putObject(builder.build(), RequestBody.fromBytes(bytes));
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

    private String determineContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    /**
     * Makes an existing S3 object publicly readable by setting its ACL to PUBLIC_READ.
     * Use this for video objects that need to be accessible by Facebook/Instagram crawlers.
     */
    public void makeObjectPublic(String key) {
        try {
            PutObjectAclRequest aclRequest = PutObjectAclRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build();
            s3Client.putObjectAcl(aclRequest);
            logger.info("✅ [S3] Made object public: {}", key);
        } catch (Exception e) {
            logger.warn("⚠️ [S3] Failed to make object public: {}. Error: {}", key, e.getMessage());
        }
    }

    /**
     * Resolves a video URL to a publicly accessible URL.
     * If the URL is a pre-signed URL, strips query params and makes the object public-read.
     * Returns the clean permanent URL.
     */
    public String resolvePublicVideoUrl(String videoUrl) {
        if (videoUrl == null) return null;
        // Get clean URL without query params
        String cleanUrl = getPermanentUrlFromPresigned(videoUrl);
        // If it was a pre-signed URL (had query params), make the object public now
        if (videoUrl.contains("?") && videoUrl.contains(".amazonaws.com/")) {
            try {
                String key = extractKeyFromUrl(cleanUrl);
                makeObjectPublic(key);
            } catch (Exception e) {
                logger.warn("⚠️ [S3] Could not make video public: {}", e.getMessage());
            }
        }
        return cleanUrl;
    }

    /**
     * Extracts the S3 Key from a URL (permanent or pre-signed).
     */
    public String extractKeyFromUrl(String url) {
        if (url == null || !url.contains(".amazonaws.com/")) {
            return url;
        }
        try {
            // URL format: https://bucket.s3.region.amazonaws.com/path/to/object?query
            // or https://bucket.s3.amazonaws.com/path/to/object
            String pathPart = url.split("\\.amazonaws\\.com/")[1];
            // Remove query parameters if present
            if (pathPart.contains("?")) {
                pathPart = pathPart.substring(0, pathPart.indexOf("?"));
            }
            return pathPart;
        } catch (Exception e) {
            logger.warn("Failed to extract key from URL: {}. Returning original.", url);
            return url;
        }
    }

    /**
     * Generates a pre-signed URL for public read access (e.g. for Instagram/Facebook media ingestion).
     * The URL is valid for the given duration and does NOT force a download.
     */
    public String generatePresignedReadUrl(String key, Duration duration) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    /**
     * Convenience overload — generates a pre-signed read URL valid for 1 hour.
     */
    public String generatePresignedReadUrl(String key) {
        return generatePresignedReadUrl(key, Duration.ofHours(1));
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
