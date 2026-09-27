package ai.qorva.core.it;

import com.stripe.Stripe;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The Stripe webhook end to end, signed with the test secret exactly as Stripe signs: each event is
 * handled once per id, events for customers Qorva cannot place are acknowledged instead of retried
 * forever, and only a valid signature gets in.
 */
class StripeWebhookIntegrationTest extends AbstractIntegrationTest {

	private static final String SECRET = "whsec_test";   // stripe.webhook.secret in application-test.yml

	@Autowired private TwoTenantFixture fixture;
	@Autowired private MongoTemplate mongo;

	private TwoTenantFixture.SeededTenant a;

	@BeforeEach
	void seed() {
		a = fixture.reset().a();
		mongo.getCollection("stripe_webhook_events").deleteMany(new Document());
		mongo.getCollection("stripe_product_references").deleteMany(new Document());
	}

	@Test
	void anEventIsHandledOnce_evenWhenStripeDeliversItAgain() throws Exception {
		var event = event("evt_product_1", "product.created",
			"{\"id\":\"prod_1\",\"object\":\"product\",\"name\":\"Pro\",\"active\":true}");

		assertThat(deliver(event)).isEqualTo("success");
		assertThat(deliver(event)).isEqualTo("duplicate");

		assertThat(mongo.getCollection("stripe_product_references").countDocuments()).isEqualTo(1);
		var ledger = mongo.getCollection("stripe_webhook_events").find(new Document("_id", "evt_product_1")).first();
		assertThat(ledger).isNotNull();
		assertThat(ledger.getString("status")).isEqualTo("PROCESSED");
	}

	@Test
	void aLoggedEventLandsOnItsTenant_once() throws Exception {
		var customerId = mongo.getCollection("tenants").find(new Document("_id", new ObjectId(a.tenantId()))).first()
			.getString("stripeCustomerId");
		var event = event("evt_customer_1", "customer.updated",
			"{\"id\":\"" + customerId + "\",\"object\":\"customer\",\"email\":\"" + a.ownerEmail() + "\"}");

		deliver(event);
		deliver(event);

		var logs = mongo.getCollection("stripe_event_logs").find(new Document("eventType", "customer.updated")).into(new java.util.ArrayList<>());
		assertThat(logs).hasSize(1);
		assertThat(logs.getFirst().get("tenantId")).isEqualTo(new ObjectId(a.tenantId()));
	}

	@Test
	void aCustomerQorvaCannotPlace_isAcknowledged_notRetriedForever() throws Exception {
		var event = event("evt_customer_2", "customer.created",
			"{\"id\":\"cus_unknown\",\"object\":\"customer\",\"email\":\"nobody@nowhere.test\"}");

		assertThat(deliver(event)).isEqualTo("success");
		assertThat(mongo.getCollection("stripe_event_logs").countDocuments(new Document("stripeCustomerId", "cus_unknown"))).isZero();
	}

	@Test
	void anUnhandledTypeIsAcknowledged_withoutALedgerRow() throws Exception {
		assertThat(deliver(event("evt_charge_1", "charge.succeeded", "{\"id\":\"ch_1\",\"object\":\"charge\"}"))).isEqualTo("success");
		assertThat(mongo.getCollection("stripe_webhook_events").countDocuments()).isZero();
	}

	@Test
	void aBadSignatureIsRefused() throws Exception {
		var response = mvc.perform(post("/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", "t=1,v1=00").content(event("evt_x", "product.created", "{}")))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(mongo.getCollection("stripe_webhook_events").countDocuments()).isZero();
	}

	private String deliver(String payload) throws Exception {
		var timestamp = System.currentTimeMillis() / 1000;
		var mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		var signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
		var response = mvc.perform(post("/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", "t=" + timestamp + ",v1=" + signature).content(payload))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		return response.getContentAsString();
	}

	private static String event(String id, String type, String object) {
		return "{\"id\":\"" + id + "\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION + "\","
			+ "\"created\":1758880000,\"type\":\"" + type + "\",\"data\":{\"object\":" + object + "}}";
	}
}
