package ai.qorva.core.utils;

import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

@UtilityClass
public class BuildApiResponse {
	public <D extends QorvaDTO> ResponseEntity<QorvaRequestResponse> from(D data) {
		return ResponseEntity.ok(buildResponse(data));
	}

	public <D extends QorvaDTO> ResponseEntity<QorvaRequestResponse> from(Object data) {
		return ResponseEntity.ok(buildResponse(data));
	}

	public <D extends QorvaDTO> ResponseEntity<QorvaRequestResponse> from(Page<D> data) {
		return ResponseEntity.ok(buildResponse(data));
	}

	public ResponseEntity<QorvaRequestResponse> from(Boolean ok) {
		return ResponseEntity.ok(buildResponse(ok));
	}

	private QorvaRequestResponse buildResponse(Object data) {
		return QorvaRequestResponse
			.builder()
				.data(data)
				.code(HttpStatus.OK.value())
				.timestamp(Instant.now())
			.build();
	}
}
