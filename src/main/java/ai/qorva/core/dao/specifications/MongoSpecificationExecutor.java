package ai.qorva.core.dao.specifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface MongoSpecificationExecutor<T> {

    List<T> findAll(MongoSpecification<T> specification);

    List<T> findAll(MongoSpecification<T> specification, Sort sort);

    Page<T> findAll(MongoSpecification<T> specification, Pageable pageable);

    Optional<T> findOne(MongoSpecification<T> specification);

    boolean exists(MongoSpecification<T> specification);

    long count(MongoSpecification<T> specification);
}