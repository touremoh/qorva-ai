package ai.qorva.core.config;

import ai.qorva.core.dto.StripeWebhookDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "stripe")
@NoArgsConstructor
@AllArgsConstructor
public class StripeProperties {
	private String secretKey;
	private StripeWebhookDto webhook;
}
