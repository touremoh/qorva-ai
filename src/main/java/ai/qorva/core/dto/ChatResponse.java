package ai.qorva.core.dto;

import java.util.List;

public record ChatResponse(
	String cvId,
	String jobPostId,
	String sessionId,
	String prompt,
	List<String> responses
) {}
