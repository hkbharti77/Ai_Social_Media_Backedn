package com.aiplatform.controller;

import com.aiplatform.dto.MediaUploadRequest;
import com.aiplatform.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aiplatform.security.UserDetailsImpl;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/media")
@CrossOrigin(origins = "*", maxAge = 3600)
public class MediaController {

    @Autowired
    private S3Service s3Service;

    /**
     * Endpoint to upload a real file (multipart/form-data) to AWS S3.
     * Returns the public pre-signed URL of the uploaded file.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload"));
            }

            // Get current user
            UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Long userId = userDetails.getId();

            // Basic type validation
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only images and videos are allowed"));
            }

            // Upload to S3
            String fileUrl = s3Service.uploadFile(file, userId);

            return ResponseEntity.ok(Map.of(
                "url", fileUrl,
                "downloadUrl", s3Service.generateDownloadUrl(s3Service.extractKeyFromUrl(fileUrl)),
                "fileName", file.getOriginalFilename(),
                "contentType", contentType,
                "size", file.getSize()
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "File upload failed: " + e.getMessage()));
        }
    }

    /**
     * Endpoint to register an already-hosted media URL (e.g., a pre-signed S3 URL from AI generation).
     * Accepts JSON body: { "file": "https://..." }
     * Returns the URL so the frontend can use it directly.
     */
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadMediaUrl(@RequestBody MediaUploadRequest request) {
        try {
            String url = s3Service.getPermanentUrlFromPresigned(request.getFile());
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File URL must not be empty"));
            }

            // Return the sanitized URL — ensure it's a permanent S3 URL if it was pre-signed
            return ResponseEntity.ok(Map.of(
                "url", url,
                "downloadUrl", s3Service.generateDownloadUrl(s3Service.extractKeyFromUrl(url)),
                "fileName", url.substring(url.lastIndexOf('/') + 1).split("\\?")[0],
                "contentType", "image/png",
                "size", 0
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "URL upload failed: " + e.getMessage()));
        }
    }

    /**
     * Endpoint to list all media URLs for the current user from S3.
     */
    @GetMapping("/all")
    public ResponseEntity<?> listAllMedia() {
        try {
            UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Long userId = userDetails.getId();
            return ResponseEntity.ok(s3Service.listAllFiles(userId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to list media: " + e.getMessage()));
        }
    }

    /**
     * Endpoint to delete a media file from S3 using its URL or Key.
     */
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteMedia(@RequestParam("url") String url) {
        try {
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "URL must not be empty"));
            }

            UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Long userId = userDetails.getId();

            String key = s3Service.extractKeyFromUrl(url);
            
            // Security check: Ensure the key belongs to the user's folder
            String userFolderPrefix = "social_media/" + userId + "/";
            if (!key.startsWith(userFolderPrefix)) {
                return ResponseEntity.status(403).body(Map.of("error", "You are not authorized to delete this file"));
            }

            s3Service.deleteFile(key);
            return ResponseEntity.ok(Map.of("message", "File deleted successfully", "url", url));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete file: " + e.getMessage()));
        }
    }
}
