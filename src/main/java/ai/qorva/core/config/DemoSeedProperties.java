package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for demo sample-data seeding from S3.
 * Fixtures live at {@code s3://<bucket>/<prefix>/<version>/<recruitment-slug>/<lang>/{cvs.json,job-posts.json}}.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "qorva.demo-seed")
public class DemoSeedProperties {

	/** Whether demo seeding is enabled. */
	private boolean enabled = true;

	/** S3 bucket holding the fixtures. Falls back to the shared app bucket when blank. */
	private String bucket;

	/** Root key prefix for fixtures. */
	private String prefix = "demo-seed";

	/** Fixture set version segment (allows non-breaking fixture updates). */
	private String version = "v1";

	/** Language used when a requested (segment, language) fixture set is missing. */
	private String fallbackLanguage = "en";
}
