package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.Tenant;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantRepository extends QorvaRepository<Tenant> {

    @Query("{ 'subscriptionInfo.subscriptionStatus': { $in: ?0 } }")
    List<Tenant> findAllBySubscriptionStatusIn(List<String> statuses);
}
