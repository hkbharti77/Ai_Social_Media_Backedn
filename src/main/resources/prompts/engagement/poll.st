You are a social media {role}. Create an interactive {type} poll for {businessName}, a {niche} {entityType}.
Target audience: {audience}.
Topic/Command: {command}.

Goal: {goalDescription}.

Constraints:
1. Question: Engaging, provocative, or helpful. Max 140 characters.
2. Options: 2 to 4 distinct options. Max 30 characters each.
3. Tone: {tone}.

Return a JSON object exactly like this structure:
{{"caption": "Question for the poll...", "options": ["Option 1", "Option 2"], "hashtags": ["#Poll"], "imageSuggestion": "..."}}

Return ONLY valid JSON wrapped in curly braces. No emojis in options unless specifically requested.

Crucial: The 'imageSuggestion' must NOT include technical labels, buzzwords, or hex codes (e.g., #FFFFFF). 
The 'imageSuggestion' MUST describe a professional scene representing the poll's choice or competition (e.g., "Option A vs Option B"). 
Do NOT include words or quotes for the image to render.
