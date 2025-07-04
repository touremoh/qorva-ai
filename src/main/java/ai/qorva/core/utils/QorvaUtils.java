package ai.qorva.core.utils;

import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
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

	public List<String> splitString(String input) {
		if (isEmpty(input)) {
			return List.of();
		}
		return Arrays.stream(input.split("[^\\p{L}\\p{M}']+"))
			.map(String::toLowerCase)
			.toList();
	}

	public void merge(Object target, Object source) throws QorvaException {
		if (Objects.isNull(target) || Objects.isNull(source)) {
			throw new QorvaException("Target and source objects must not be null.");
		}

		Class<?> clazz = target.getClass();

		// Iterate over all fields of the class
		for (Field field : clazz.getDeclaredFields()) {
			// Allow private fields to be accessed
			field.setAccessible(true);

			try {
				// Get value of the field in target
				Object targetValue = field.get(target);

				// Get value of the field in source
				Object sourceValue = field.get(source);

				// Update only if the target field is null and the source field is not null
				if (targetValue == null && sourceValue != null) {
					field.set(target, sourceValue); // Set target field to source field value
				}
			} catch (IllegalAccessException e) {
				throw new QorvaException("Could not access field: " + field.getName(), e);
			}
		}
	}

	public Instant getFirstDayOfMonth() {
		ZoneId zone = ZoneId.of("UTC");
		Instant now = Instant.now();

		// Convert Instant to LocalDate
		LocalDate currentDate = now.atZone(zone).toLocalDate();

		// The First and last day of the month
		LocalDate firstDay = currentDate.withDayOfMonth(1);

		// Return the first day of the month
		return firstDay.atStartOfDay(zone).toInstant();
	}

	public Instant getLastDayOfMonth() {
		ZoneId zone = ZoneId.of("UTC");
		Instant now = Instant.now();

		// Convert Instant to LocalDate
		LocalDate currentDate = now.atZone(zone).toLocalDate();

		// First and last day of month
		LocalDate lastDay = currentDate.withDayOfMonth(currentDate.lengthOfMonth());

		// Get the last day of the month
		return lastDay.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1);
	}
}
