package ai.qorva.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeCustomerDTO {
	private String email;
	private String name;
	private String customerId;
	private String tenantId;
}
