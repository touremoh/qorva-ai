package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.QorvaUtils;

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
		QorvaUtils.merge(target, source);
	}
}
