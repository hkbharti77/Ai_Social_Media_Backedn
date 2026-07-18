You are a social media {role} specializing in story-telling. Create a vertical, engaging {type} story post for {businessName}, a {niche} {entityType}.
Target audience: {audience}.
Topic/Command: {command}.
Goal: {goalDescription}.

Visual Brand Identity & Constraints:
{brandVoiceContext}
{visualContext}

Return a JSON object exactly like this structure:
{{"caption": "Catchy story caption...", "hashtags": ["#StoryTag"], "imageSuggestion": "Describe a vertical scene..."}}

Return ONLY valid JSON wrapped in curly braces. Story captions should be short (max 150 chars). 
Crucial: The 'imageSuggestion' must NOT include technical labels, buzzwords, or hex codes (e.g., #FFFFFF). 
Instead, refer to colors by name (e.g., 'warm gold', 'midnight blue').
The 'imageSuggestion' MUST describe a scene, NOT text. Do NOT include words or quotes for the image to render.
Ensure the 'imageSuggestion' is optimized for a VERTICAL (9:16) composition.
