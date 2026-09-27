package ai.qorva.core.it;

import ai.qorva.core.runner.StripeProductSyncRunner;
import ai.qorva.core.service.S3StorageService;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

/**
 * Base of the integration suite: the whole application against a real MongoDB (Atlas local image,
 * started once per JVM and shared by every subclass so the Spring context is cached too).
 *
 * <p>Mongock runs every changeunit on the empty database at startup, so booting is itself a
 * migration test. Outbound services that would call the network at startup or on common paths
 * (Stripe catalog sync, S3) are mocked; everything else is the production wiring.</p>
 *
 * <p>Skipped, not failed, when Docker is unavailable, so {@code ./mvnw test} still runs the unit
 * suite on a machine without Docker. CI always has Docker.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("ai.qorva.core.it.AbstractIntegrationTest#dockerAvailable")
public abstract class AbstractIntegrationTest {

	static final MongoDBAtlasLocalContainer MONGO = new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:8.0");

	static {
		if (dockerAvailable()) {
			MONGO.start();
		}
	}

	/** Boot 3.5 has no @ServiceConnection for the Atlas local container, so the URI is wired by hand. */
	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", () -> MONGO.getDatabaseConnectionString());
	}

	@MockitoBean
	protected StripeProductSyncRunner stripeProductSyncRunner;

	@MockitoBean
	protected S3StorageService s3StorageService;

	@Autowired
	protected MockMvc mvc;

	static boolean dockerAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		} catch (Exception e) {
			return false;
		}
	}
}
