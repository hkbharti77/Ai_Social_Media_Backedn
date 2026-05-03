package com.aiplatform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/**
 * Transcodes videos to Instagram/Facebook Reels-compatible format using FFmpeg.
 *
 * Instagram Reels requirements (error 2207026 = format mismatch):
 * - Container: MP4, moov atom at front (faststart)
 * - Video codec: H.264 (libx264), progressive, yuv420p (4:2:0)
 * - Audio codec: AAC, max 48kHz, max 128kbps, stereo
 * - Frame rate: 23–60 FPS
 * - Max width: 1920px
 * - Aspect ratio: 9:16 recommended for Reels
 * - Max bitrate: 25 Mbps
 * - Duration: 3s – 15 min
 * - File size: max 300 MB
 */
@Service
@Slf4j
public class VideoTranscodeService {

    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private S3Service s3Service;

    /**
     * Downloads a video from S3, transcodes it to Instagram-compatible H.264 MP4,
     * re-uploads to S3 with public-read, and returns the new public URL.
     */
    public String transcodeForInstagram(String videoUrl, Long userId) {
        Path inputFile = null;
        Path outputFile = null;
        try {
            // 1. Download video bytes
            log.info("⬇️ [Transcode] Downloading video: {}", videoUrl);
            byte[] videoBytes = restTemplate.getForObject(videoUrl, byte[].class);
            if (videoBytes == null || videoBytes.length == 0) {
                throw new RuntimeException("Failed to download video from: " + videoUrl);
            }

            // 2. Write to temp file
            inputFile = Files.createTempFile("veo_input_", ".mp4");
            Files.write(inputFile, videoBytes);
            log.info("📁 [Transcode] Input: {} ({} bytes)", inputFile, videoBytes.length);

            // 3. Prepare output temp file
            outputFile = Files.createTempFile("veo_transcoded_", ".mp4");

            // 4. FFmpeg command — ULTRA-STRICT Instagram Reels compliance
            // Instagram Reels API Requirements (2026):
            //   Duration: 3-90 seconds
            //   Resolution: 1080x1920 (9:16 aspect ratio)
            //   Codec: H.264, progressive scan, closed GOP, 4:2:0 chroma
            //   Audio: AAC, 48kHz max, stereo
            //   Frame rate: 30fps
            //   Container: MP4 with faststart
            String resolvedFfmpeg = resolveFfmpegPath();
            String[] cmd = {
                resolvedFfmpeg,
                "-y",
                "-i", inputFile.toString(),
                // Limit duration to 90 seconds (Instagram API max)
                "-t", "90",
                // Force 9:16 aspect ratio (1080x1920) with black padding if needed
                "-vf", "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:black,format=yuv420p",
                "-c:v", "libx264",
                "-profile:v", "high",                           // high profile for better quality
                "-level", "4.2",                                // level 4.2 for 1080p
                "-pix_fmt", "yuv420p",                          // 4:2:0 chroma (required)
                "-r", "30",                                     // 30fps
                "-g", "60",                                     // closed GOP every 60 frames (2 sec at 30fps)
                "-keyint_min", "60",                            // minimum GOP size
                "-sc_threshold", "0",                           // disable scene detection (ensures closed GOP)
                "-bf", "2",                                     // 2 B-frames
                "-flags", "+cgop",                              // closed GOP flag
                "-movflags", "+faststart",                      // moov atom at front
                "-c:a", "aac",
                "-b:a", "128k",
                "-ar", "48000",                                 // 48kHz (max for Instagram)
                "-ac", "2",                                     // stereo
                "-b:v", "5000k",                                // 5 Mbps video bitrate (Instagram recommended)
                "-maxrate", "5000k",
                "-bufsize", "10000k",
                "-preset", "slow",                              // slower = better quality
                "-tune", "film",                                // optimize for film content
                "-avoid_negative_ts", "make_zero",
                "-fflags", "+genpts",                           // regenerate timestamps from 0
                outputFile.toString()
            };

            log.info("🎬 [Transcode] Running FFmpeg: {}", resolvedFfmpeg);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Capture FFmpeg output
            StringBuilder ffmpegLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ffmpegLog.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("❌ [Transcode] FFmpeg failed (exit {}): {}", exitCode, ffmpegLog);
                throw new RuntimeException("FFmpeg transcoding failed with exit code: " + exitCode);
            }
            log.info("✅ [Transcode] FFmpeg completed successfully");

            // 5. Upload transcoded video to S3 with public-read
            String newFileName = "veo_transcoded_" + userId + "_" + UUID.randomUUID() + ".mp4";
            byte[] transcodedBytes = Files.readAllBytes(outputFile);
            log.info("⬆️ [Transcode] Uploading transcoded video ({} bytes) to S3...", transcodedBytes.length);

            String newUrl = s3Service.uploadFile(
                newFileName,
                new ByteArrayInputStream(transcodedBytes),
                userId,
                true  // public-read
            );

            log.info("✅ [Transcode] Done. New URL: {}", newUrl);
            return newUrl;

        } catch (Exception e) {
            log.error("❌ [Transcode] Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Video transcoding failed: " + e.getMessage(), e);
        } finally {
            deleteSilently(inputFile);
            deleteSilently(outputFile);
        }
    }

    /**
     * Resolves the FFmpeg executable path.
     * Tries configured path first, then common Windows/Linux locations.
     */
    private String resolveFfmpegPath() {
        // 1. Try configured path
        if (ffmpegPath != null && !ffmpegPath.equals("ffmpeg")) {
            File f = new File(ffmpegPath);
            if (f.exists() && f.canExecute()) {
                log.info("✅ [Transcode] Using configured FFmpeg: {}", ffmpegPath);
                return ffmpegPath;
            }
        }

        // 2. Try common Windows locations
        String[] windowsPaths = {
            "D:\\ffmpeg\\ffmpeg-8.1-essentials_build\\bin\\ffmpeg.exe",
            "C:\\ffmpeg\\bin\\ffmpeg.exe",
            "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe"
        };
        for (String path : windowsPaths) {
            if (new File(path).exists()) {
                log.info("✅ [Transcode] Found FFmpeg at: {}", path);
                return path;
            }
        }

        // 3. Try Linux/Mac locations
        String[] unixPaths = { "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg" };
        for (String path : unixPaths) {
            if (new File(path).exists()) {
                log.info("✅ [Transcode] Found FFmpeg at: {}", path);
                return path;
            }
        }

        // 4. Fall back to PATH
        log.warn("⚠️ [Transcode] FFmpeg not found at known paths, trying system PATH");
        return "ffmpeg";
    }

    /**
     * Checks if FFmpeg is available.
     */
    public boolean isFfmpegAvailable() {
        try {
            String path = resolveFfmpegPath();
            Process p = new ProcessBuilder(path, "-version").start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteSilently(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {}
        }
    }
}
