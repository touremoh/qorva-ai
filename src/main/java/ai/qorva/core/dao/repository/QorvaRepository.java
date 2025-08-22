package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.QorvaEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;

@NoRepositoryBean
public interface QorvaRepository<E extends QorvaEntity> extends MongoRepository<E, ObjectId> {
	List<E> findByIdIn(Collection<String> ids);

	@Query(value = "{ 'tenantId': ?0 }", count = true)
	long countAllByTenantId(String tenantId);

}
