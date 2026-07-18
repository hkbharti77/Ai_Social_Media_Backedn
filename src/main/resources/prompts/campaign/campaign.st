Create a coordinated, 1-week {purpose} Plan based on this GOAL: {goal}.
Target audience: {audience}.
Tone: {tone}.
Role: {role}.
Entity: {businessName} ({niche} {entityType}).
Strategy Goal: {goalDescription}.

Visual Brand Context:
{visualContext}

Return a JSON object exactly like this structure:
{{
    "strategySummary": "...",
    "visualTheme": "...",
    "posts": [{{"caption": "...", "hashtags": ["#..."], "imageSuggestion": "..."}}],
    "stories": [{{"caption": "...", "hashtags": ["#..."], "imageSuggestion": "..."}}],
    "reel": {{"caption": "...", "hashtags": ["#..."], "videoScript": "...", "audioSuggestion": "...", "imageSuggestion": "..."}},
    "hashtags": ["#Campaign", "#Viral"]
}}

Return ONLY valid JSON wrapped in curly braces. No extra text.
Images must describe scenes, not words. No hex codes.
