package ai.qorva.core.config;

import ai.qorva.core.service.BulkCvUploadService;
import ai.qorva.core.service.CVService;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the silent >50-file upload failure: Tomcat >= 10.1.42 defaults
 * maxPartCount to 50, so every advertised file cap must stay under an explicit
 * server.tomcat.max-part-count override in application.yml.
 */
class TomcatMultipartConfigTest {

	@SuppressWarnings("unchecked")
	private int configuredMaxPartCount() {
		InputStream yml = getClass().getResourceAsStream("/application.yml");
		assertThat(yml).isNotNull();
		Map<String, Object> root = new Yaml().load(yml);
		var server = (Map<String, Object>) root.get("server");
		assertThat(server).as("server block in application.yml").isNotNull();
		var tomcat = (Map<String, Object>) server.get("tomcat");
		assertThat(tomcat).as("server.tomcat block in application.yml").isNotNull();
		var maxPartCount = tomcat.get("max-part-count");
		assertThat(maxPartCount).as("server.tomcat.max-part-count").isNotNull();
		return ((Number) maxPartCount).intValue();
	}

	@Test
	void maxPartCountCoversSyncUploadCap() {
		// One part per file plus headroom for auxiliary form fields.
		assertThat(configuredMaxPartCount()).isGreaterThan(CVService.SYNC_UPLOAD_MAX_FILES);
	}

	@Test
	void maxPartCountCoversBulkStagingChunk() {
		assertThat(configuredMaxPartCount()).isGreaterThan(BulkCvUploadService.MAX_FILES_PER_CHUNK);
	}
}
