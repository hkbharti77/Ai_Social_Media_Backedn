package com.aiplatform.model;

import lombok.Getter;

/**
 * Video credit packs available for purchase.
 *
 * Pricing (always profitable):
 *   Veo Lite cost  = ₹34/video  → sell at ₹44.9+ per video
 *   Veo Fast cost  = ₹102/video → sell at ₹119.9+ per video
 *   Veo Standard   = ₹390/video → sell at ₹429.9+ per video
 *
 * Manual (1/2/3 videos) — highest margin:
 *   Lite:     ₹55 / ₹100 / ₹145   → profit ₹21 / ₹16 / ₹15 per video
 *   Fast:     ₹140 / ₹265 / ₹385  → profit ₹38 / ₹30 / ₹25 per video
 *   Standard: ₹470 / ₹900 / ₹1,299→ profit ₹80 / ₹60 / ₹43 per video
 */
@Getter
public enum VideoCreditPack {

    // ── Veo Lite Packs ────────────────────────────────────────────────────────
    LITE_MANUAL_1 ("veo-lite", 1,  55,   "1 Veo Lite Video",   "Manual"),
    LITE_MANUAL_2 ("veo-lite", 2,  100,  "2 Veo Lite Videos",  "Manual"),
    LITE_MANUAL_3 ("veo-lite", 3,  145,  "3 Veo Lite Videos",  "Manual"),
    LITE_PACK_10  ("veo-lite", 10, 449,  "10 Veo Lite Videos", "Pack"),
    LITE_PACK_30  ("veo-lite", 30, 1199, "30 Veo Lite Videos", "Pack"),
    LITE_PACK_50  ("veo-lite", 50, 1899, "50 Veo Lite Videos", "Pack"),
    LITE_PACK_100 ("veo-lite", 100,3799, "100 Veo Lite Videos","Pack"),

    // ── Veo Fast Packs ────────────────────────────────────────────────────────
    FAST_MANUAL_1 ("veo-fast", 1,  140,  "1 Veo Fast Video",   "Manual"),
    FAST_MANUAL_2 ("veo-fast", 2,  265,  "2 Veo Fast Videos",  "Manual"),
    FAST_MANUAL_3 ("veo-fast", 3,  385,  "3 Veo Fast Videos",  "Manual"),
    FAST_PACK_10  ("veo-fast", 10, 1199, "10 Veo Fast Videos", "Pack"),
    FAST_PACK_30  ("veo-fast", 30, 3299, "30 Veo Fast Videos", "Pack"),
    FAST_PACK_50  ("veo-fast", 50, 5499, "50 Veo Fast Videos", "Pack"),
    FAST_PACK_100 ("veo-fast", 100,10999,"100 Veo Fast Videos","Pack"),

    // ── Veo Standard Packs ───────────────────────────────────────────────────
    STANDARD_MANUAL_1 ("veo-standard", 1,  470,  "1 Veo Standard Video",   "Manual"),
    STANDARD_MANUAL_2 ("veo-standard", 2,  900,  "2 Veo Standard Videos",  "Manual"),
    STANDARD_MANUAL_3 ("veo-standard", 3,  1299, "3 Veo Standard Videos",  "Manual"),
    STANDARD_PACK_10  ("veo-standard", 10, 4299, "10 Veo Standard Videos", "Pack"),
    STANDARD_PACK_30  ("veo-standard", 30, 12499,"30 Veo Standard Videos", "Pack"),
    STANDARD_PACK_50  ("veo-standard", 50, 20499,"50 Veo Standard Videos", "Pack"),
    STANDARD_PACK_100 ("veo-standard", 100,40999,"100 Veo Standard Videos","Pack");

    private final String modelId;       // veo-lite / veo-fast / veo-standard
    private final int videoCount;       // Number of video credits
    private final long priceInr;        // Price in INR
    private final String displayName;   // User-facing name
    private final String packType;      // Manual or Pack

    VideoCreditPack(String modelId, int videoCount, long priceInr, String displayName, String packType) {
        this.modelId = modelId;
        this.videoCount = videoCount;
        this.priceInr = priceInr;
        this.displayName = displayName;
        this.packType = packType;
    }

    /** Price per video in this pack */
    public double pricePerVideo() {
        return (double) priceInr / videoCount;
    }

    /** Your cost per video (Google API cost) */
    public double costPerVideo() {
        return switch (modelId) {
            case "veo-lite"     -> 34.0;
            case "veo-fast"     -> 102.0;
            case "veo-standard" -> 390.0;
            default             -> 0.0;
        };
    }

    /** Profit per video */
    public double profitPerVideo() {
        return pricePerVideo() - costPerVideo();
    }

    /** Find pack by its enum name (case-insensitive) */
    public static VideoCreditPack fromName(String name) {
        for (VideoCreditPack pack : values()) {
            if (pack.name().equalsIgnoreCase(name)) return pack;
        }
        throw new IllegalArgumentException("Unknown video credit pack: " + name);
    }
}
