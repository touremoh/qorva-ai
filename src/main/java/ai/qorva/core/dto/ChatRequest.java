package ai.qorva.core.dto;

public record ChatRequest(
	String cvId,
	String jobPostId,
	String sessionId,
	String prompt
) {}
