package com.aiplatform.model;

public enum BrandVoiceMode {
    STYLE_DNA,    // Uses analyzed text summary (+2 credits)
    FULL_CONTEXT, // Uses raw text + image analysis (+5 credits)
    NONE          // No personalization (0 credits)
}
