package ai.qorva.core.dto;

public record CheckoutResponse(
	String sessionId,
	String customerId
) {}
