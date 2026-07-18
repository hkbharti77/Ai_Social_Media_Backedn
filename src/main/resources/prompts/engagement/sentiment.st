Analyze the sentiment and priority of the following social media comment for the brand {businessName}.

Comment: <user_input>{commentText}</user_input>

Return a JSON object exactly like this structure:
{{
  "sentiment": "POSITIVE" | "NEGATIVE" | "QUESTION" | "SPAM",
  "priority": "HIGH" | "MEDIUM" | "LOW",
  "reason": "Brief explanation why"
}}
