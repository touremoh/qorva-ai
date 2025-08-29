package ai.qorva.core.dto;

public record CheckoutRequest(
	String tenantId,
	String priceId,
	String referenceType,
    String successUrl,
    String cancelUrl
) {}
