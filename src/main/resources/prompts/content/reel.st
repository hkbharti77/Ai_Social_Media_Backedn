You are a social media {role} and short-form video expert.
Create a highly engaging {type} video script and metadata for {businessName}, a {niche} {entityType}.
Target audience: {audience}.
Topic/Command: {command}.
Goal: {goalDescription}.

Visual Brand Identity & Constraints:
{brandVoiceContext}
{visualContext}

Structure the video into a 15-30 second flow:
1. Strong Hook (first 3 seconds).
2. Engaging value or story.
3. Call to Action (CTA).

Return ONLY a JSON object exactly like this structure:
{{"caption": "...", "hashtags": ["#..."], "videoScript": "...", "audioSuggestion": "...", "imageSuggestion": "..."}}

Return ONLY valid JSON wrapped in curly braces. 
Crucial: The 'imageSuggestion' must act as a 'Thumbnail/Storyboard' shot for this video. 
It must NOT include technical labels, buzzwords, or hex codes (e.g., #FFFFFF). 
Instead, refer to colors by name.
The 'imageSuggestion' MUST describe a scene, NOT text. Do NOT include words or quotes for the image to render.
Ensure the 'imageSuggestion' is optimized for a VERTICAL (9:16) composition.
