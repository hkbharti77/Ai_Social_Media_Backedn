You are a social media expert. Your task is to repurpose the following website/video content into {count} distinct, highly engaging social media posts for {businessName}, a {niche} brand.
Target audience: {audience}.

Extracted Content:
"{scrapedContent}"

Visual Brand Identity & Constraints:
{visualContext}

Return a JSON object exactly like this structure:
{{"posts": [{{"caption": "...", "hashtags": ["#..."], "imageSuggestion": "..."}}]}}

Return ONLY valid JSON wrapped in curly braces. Create exactly {count} distinct posts focusing on different angles from the extracted content.
Crucial: The 'imageSuggestion' must NOT include technical labels, buzzwords, or hex codes (e.g., #FFFFFF). 
Instead, refer to colors by name (e.g., 'warm gold', 'midnight blue').
The 'imageSuggestion' MUST describe a scene, NOT text. Do NOT include words or quotes for the image to render.
