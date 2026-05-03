package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.QorvaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface QorvaRepositorySpecification<E extends QorvaEntity> {
	List<E> findAll(MongoSpecification<E> specification);

	List<E> findAll(MongoSpecification<E> specification, Sort sort);

	Page<E> findAll(MongoSpecification<E> specification, Pageable pageable);

	Optional<E> findOne(MongoSpecification<E> specification);

	boolean exists(MongoSpecification<E> specification);

	long count(MongoSpecification<E> specification);
}
