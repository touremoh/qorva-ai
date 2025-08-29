package ai.qorva.core.dto;

public record ChatResult(String content, Long promptTokens, Long completionTokens, String model) {}