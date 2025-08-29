package ai.qorva.core.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Builder
public class QorvaRequestResponse implements Serializable {
	private Object data;
	private int code;
	private Instant timestamp;
}
