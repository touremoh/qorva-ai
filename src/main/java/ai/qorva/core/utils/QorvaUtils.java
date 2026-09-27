package ai.qorva.core.utils;

import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@UtilityClass
public class QorvaUtils {

	public String toJSON(Object object) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(new JavaTimeModule());
			return mapper.writeValueAsString(object);
		} catch (JsonProcessingException e) {
			log.error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}

	public void patchLeft(Object target, Object source) throws QorvaException {
		if (Objects.isNull(target) || Objects.isNull(source)) {
			throw new QorvaException("Target and source objects must not be null.");
		}

		for (Field field : target.getClass().getDeclaredFields()) {
			field.setAccessible(true);
			try {
				Object targetValue = field.get(target);
				Object sourceValue = field.get(source);

				if (targetValue == null && sourceValue != null) {
					field.set(target, sourceValue);
				} else if (targetValue != null && sourceValue != null && isRecursable(field.getType())) {
					patchLeft(targetValue, sourceValue);
				}
			} catch (IllegalAccessException e) {
				throw new QorvaException("Could not access field: " + field.getName(), e);
			}
		}
	}

	private boolean isRecursable(Class<?> type) {
		return !type.isPrimitive()
			&& !type.isEnum()
			&& !type.isArray()
			&& !Collection.class.isAssignableFrom(type)
			&& !Map.class.isAssignableFrom(type)
			&& type.getPackageName().startsWith("ai.qorva");
	}

}
