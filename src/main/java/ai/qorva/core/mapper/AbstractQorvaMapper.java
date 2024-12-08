package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.exception.QorvaException;

import java.lang.reflect.Field;

public interface AbstractQorvaMapper<E extends QorvaEntity, D extends QorvaDTO> {

	/**
	 * Convert an entity to DTO
	 * @param o Object to convert
	 * @return DTO to return
	 */
	D map(E o);

	/**
	 * Convert a DTO to an Entity
	 * @param o Object to convert
	 * @return an entity
	 */
	E map(D o);


	default void merge(D target, D source) throws QorvaException {
		if (target == null || source == null) {
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
}
