package ai.qorva.core.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
public class QorvaErrorResponse implements Serializable {
	protected HttpStatus status;
	protected String message;
	protected Integer code;
	protected Object data;
	protected LocalDateTime timestamp;
}
