package ai.qorva.core.dto;

public record CheckoutRequest(
	String tenantId,
    String productId,
	String priceId,
    String successUrl,
    String cancelUrl
) {}
