package ai.qorva.core.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StripeWebhookDto {
	private String secret;
}
