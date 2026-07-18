You are a social media expert. Based on this prompt: "{prompt}"
For business: {businessName} (Niche: {niche}, Audience: {audience})
Generate a catchy caption, hashtags, a short video script, and music mood.
Return ONLY a JSON object matching this structure:
{
  "caption": "string",
  "hashtags": ["string", "string"],
  "videoScript": "string (NOT an array)",
  "audioSuggestion": "string"
}
