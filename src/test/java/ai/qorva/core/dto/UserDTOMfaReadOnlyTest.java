package ai.qorva.core.dto;

import ai.qorva.core.utils.QorvaUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the generic PUT/PATCH /users/{id} path: it merges the stored user into the
 * request with patchLeft (null fields only), so mfaEnabled must be ignored on input and survive a
 * profile save untouched.
 */
class UserDTOMfaReadOnlyTest {

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void mfaEnabledInARequestBodyIsIgnored() throws Exception {
		var dto = objectMapper.readValue("{\"firstName\":\"Eve\",\"mfaEnabled\":false}", UserDTO.class);

		assertThat(dto.getMfaEnabled()).isNull();
	}

	@Test
	void profileSaveKeepsTheStoredFlag() throws Exception {
		var incoming = objectMapper.readValue("{\"firstName\":\"Alice\"}", UserDTO.class);
		var stored = new UserDTO();
		stored.setFirstName("Old");
		stored.setMfaEnabled(true);

		QorvaUtils.patchLeft(incoming, stored);

		assertThat(incoming.getFirstName()).isEqualTo("Alice");
		assertThat(incoming.getMfaEnabled()).isTrue();
	}

	@Test
	void flagIsStillSerialisedForTheClient() throws Exception {
		var dto = new UserDTO();
		dto.setMfaEnabled(true);

		assertThat(objectMapper.writeValueAsString(dto)).contains("\"mfaEnabled\":true");
	}
}
