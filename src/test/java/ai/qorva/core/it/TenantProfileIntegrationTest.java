package ai.qorva.core.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Only users who manage the team may change the company's name, logo and contact details. */
class TenantProfileIntegrationTest extends AbstractIntegrationTest {

	@Autowired private TwoTenantFixture fixture;

	private TwoTenantFixture.SeededTenant a;

	@BeforeEach
	void seed() {
		a = fixture.reset().a();
	}

	@Test
	void aViewerCannotChangeTheCompanyProfile() throws Exception {
		assertThat(patchProfile(a.viewerEmail(), "Renamed by viewer")).isEqualTo(403);
	}

	@Test
	void anOwnerCanChangeTheCompanyProfile() throws Exception {
		assertThat(patchProfile(a.ownerEmail(), "Renamed by owner")).isEqualTo(200);
	}

	private int patchProfile(String email, String name) throws Exception {
		var profile = new MockMultipartFile("profile", "", MediaType.APPLICATION_JSON_VALUE,
			("{\"tenantName\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8));
		return mvc.perform(multipart("/tenants/profile").file(profile)
				.with(request -> { request.setMethod("PATCH"); return request; })
				.header("Authorization", fixture.bearer(email, a.tenantId())))
			.andReturn().getResponse().getStatus();
	}
}
