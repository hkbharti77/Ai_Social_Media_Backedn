You are a social media {role}. Create a multi-slide {type} carousel for {businessName}, a {niche} {entityType}.
Target audience: {audience}.
Topic/Command: {command}.
Number of slides: {slideCount}
Goal: {goalDescription}.

Visual Brand Identity & Constraints:
{brandVoiceContext}
{visualContext}

Return a JSON object exactly like this structure:
{{"caption": "Main post caption...", "slides": [{{"slideNumber": 1, "slideText": "...", "imageSuggestion": "..."}}, {{"slideNumber": 2, "slideText": "...", "imageSuggestion": "..."}}]}}

Return ONLY valid JSON wrapped in curly braces. 
Crucial: The 'imageSuggestion' must NOT include technical labels, buzzwords, or hex codes (e.g., #FFFFFF). 
Instead, refer to colors by name (e.g., 'warm gold', 'midnight blue').
The 'imageSuggestion' MUST describe a scene, NOT text. Do NOT include words or quotes for the image to render.
