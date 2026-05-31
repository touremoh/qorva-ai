package ai.qorva.core.dto;

import java.util.List;

public record AnswerGenerationResult(
        String conversationTitle,
        String answerText,
        List<String> followUpQuestions,
        String disclaimer
) {}
