package ai.qorva.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@UtilityClass
public class QorvaUtils {

	public String toJSON(Object object) {
		try {
			return (new ObjectMapper()).writeValueAsString(object);
		} catch (JsonProcessingException e) {
			log.error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}

	public List<String> splitString(String input) {
		if (isEmpty(input)) {
			return List.of();
		}
		return Arrays.stream(input.split("[^\\p{L}\\p{M}']+"))
			.map(String::toLowerCase)
			.toList();
	}
}
