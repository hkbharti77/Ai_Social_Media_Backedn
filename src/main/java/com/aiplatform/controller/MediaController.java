package com.aiplatform.controller;

import com.aiplatform.dto.MediaUploadRequest;
import com.aiplatform.service.S3Service;
import com.aiplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final S3Service s3Service;

    /**
     * Endpoint to upload a real file (multipart/form-data) to AWS S3.
     * Returns the public pre-signed URL of the uploaded file.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadMedia(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Please select a file to upload");
        }

        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("Authenticated user not found");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new IllegalArgumentException("Only images and videos are allowed");
        }

        String fileUrl = s3Service.uploadFile(file, userId);

        return ResponseEntity.ok(Map.of(
            "url", fileUrl,
            "downloadUrl", s3Service.generateDownloadUrl(s3Service.extractKeyFromUrl(fileUrl)),
            "fileName", file.getOriginalFilename(),
            "contentType", contentType,
            "size", file.getSize()
        ));
    }

    /**
     * Endpoint to register an already-hosted media URL.
     */
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> uploadMediaUrl(@RequestBody MediaUploadRequest request) {
        String url = s3Service.getPermanentUrlFromPresigned(request.getFile());
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("File URL must not be empty");
        }

        return ResponseEntity.ok(Map.of(
            "url", url,
            "downloadUrl", s3Service.generateDownloadUrl(s3Service.extractKeyFromUrl(url)),
            "fileName", url.substring(url.lastIndexOf('/') + 1).split("\\?")[0],
            "contentType", "image/png",
            "size", 0
        ));
    }

    /**
     * Endpoint to list all media URLs for the current user from S3.
     */
    @GetMapping("/all")
    public ResponseEntity<?> listAllMedia() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(s3Service.listAllFiles(userId));
    }

    /**
     * Endpoint to delete a media file from S3 using its URL or Key.
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteMedia(@RequestParam("url") String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be empty");
        }

        Long userId = SecurityUtils.getCurrentUserId();
        String key = s3Service.extractKeyFromUrl(url);
        
        // Security check: Ensure the key belongs to the user's folder
        String userFolderPrefix = "social_media/" + userId + "/";
        if (!key.startsWith(userFolderPrefix)) {
            throw new RuntimeException("You are not authorized to delete this file");
        }

        s3Service.deleteFile(key);
        return ResponseEntity.ok(Map.of("message", "File deleted successfully", "url", url));
    }
}
