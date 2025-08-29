package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.QorvaEntity;
import org.springframework.data.domain.Example;

public interface QorvaQueryBuilder<E extends QorvaEntity> {
	Example<E> exampleOf(E entity);
}
