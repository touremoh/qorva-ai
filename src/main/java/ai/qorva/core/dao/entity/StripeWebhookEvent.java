package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One received Stripe event (ledger for idempotent webhook handling); the id is Stripe's event id. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document("stripe_webhook_events")
public class StripeWebhookEvent {

	public static final String STATUS_PROCESSING = "PROCESSING";
	public static final String STATUS_PROCESSED = "PROCESSED";

	@Id
	private String id;
	private String eventType;
	private String status;
	private Instant createdAt;
	private Instant processedAt;
}
