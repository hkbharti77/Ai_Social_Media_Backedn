package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptEngine {
    private final PromptLoader promptLoader;

    public Prompt buildPrompt(AiRequest request) {
        String template;
        if (request.getPromptPath() != null && !request.getPromptPath().isEmpty()) {
            template = promptLoader.load(request.getPromptPath());
        } else {
            // Fallback for legacy raw templates if any still exist during migration
            template = request.getTemplate();
        }

        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("No prompt template provided (path or raw)");
        }

        PromptTemplate pt = new PromptTemplate(template);
        return pt.create(request.getTemplateParams());
    }
}
