package ai.qorva.core.admin.service;

import ai.qorva.core.admin.dto.AdminTenantData;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.enums.TenantAccountTypeEnum;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.TenantAccess;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cross-tenant reads and writes shared by the admin services. Admin work is platform-wide by design; it goes through
 * {@link MongoTemplate} with the tenant always explicit in the query, never through the tenant-scoped services.
 */
@Component
public class AdminTenantSupport {

	private final MongoTemplate mongoTemplate;
	private final TenantAccess tenantAccess;

	public AdminTenantSupport(MongoTemplate mongoTemplate, TenantAccess tenantAccess) {
		this.mongoTemplate = mongoTemplate;
		this.tenantAccess = tenantAccess;
	}

	public MongoTemplate mongo() {
		return mongoTemplate;
	}

	public Tenant requireTenant(String tenantId) throws QorvaException {
		return findTenant(tenantId).orElseThrow(() -> QorvaErrors.notFound(QorvaErrorCodes.ADMIN_TENANT_NOT_FOUND));
	}

	public Optional<Tenant> findTenant(String tenantId) {
		if (tenantId == null || !ObjectId.isValid(tenantId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(mongoTemplate.findById(new ObjectId(tenantId), Tenant.class));
	}

	public User requireUser(String tenantId, String userId) throws QorvaException {
		if (userId == null || !ObjectId.isValid(userId)) {
			throw QorvaErrors.notFound(QorvaErrorCodes.ADMIN_USER_NOT_FOUND);
		}
		var user = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(userId))
			.and("tenantId").is(new ObjectId(tenantId))), User.class);
		if (user == null) {
			throw QorvaErrors.notFound(QorvaErrorCodes.ADMIN_USER_NOT_FOUND);
		}
		return user;
	}

	/** Users of the tenant, oldest first: the first one is the account owner (same rule as the SSO break-glass). */
	public List<User> usersOf(String tenantId) {
		return mongoTemplate.find(Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId)))
			.with(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("_id"))), User.class);
	}

	/** Targeted update of the tenant document, stamping who and when. */
	public void updateTenant(String tenantId, Update update, String actor) {
		update.set("lastUpdatedAt", Instant.now()).set("lastUpdatedBy", actor);
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(tenantId))), update, Tenant.class);
		tenantAccess.evict();
	}

	/**
	 * Ends every session of the tenant's users at once: their tokens carry the old credential version. Also voids
	 * unused set-password links (they carry it too) — an invite must be re-sent after a reactivation.
	 */
	public void endSessions(String tenantId) {
		mongoTemplate.updateMulti(Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))),
			new Update().inc("passwordCredentialVersion", 1), User.class);
	}

	public void endSession(String userId) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(userId))),
			new Update().inc("passwordCredentialVersion", 1), User.class);
	}

	/** Tenants (among {@code tenantIds}) that hold a DEMO user — the demo accounts. */
	public Set<String> demoTenantIds(Collection<String> tenantIds) {
		var query = Query.query(Criteria.where("userAccountStatus").is(UserStatusEnum.DEMO.getValue()));
		if (tenantIds != null) {
			query.addCriteria(Criteria.where("tenantId").in(objectIds(tenantIds)));
		}
		var ids = new HashSet<String>();
		mongoTemplate.findDistinct(query, "tenantId", User.class, ObjectId.class).forEach(id -> ids.add(id.toHexString()));
		return ids;
	}

	public Map<String, Long> userCounts(Collection<String> tenantIds) {
		var agg = Aggregation.newAggregation(
			Aggregation.match(Criteria.where("tenantId").in(objectIds(tenantIds))),
			Aggregation.group("tenantId").count().as("n"));
		var counts = new HashMap<String, Long>();
		mongoTemplate.aggregate(agg, "users", Document.class).forEach(d ->
			counts.put(d.getObjectId("_id").toHexString(), ((Number) d.get("n")).longValue()));
		return counts;
	}

	public static String accountType(Tenant tenant, Set<String> demoTenantIds) {
		if (TenantAccountTypeEnum.TESTER.name().equals(tenant.getAccountType())) {
			return AdminTenantData.TYPE_TESTER;
		}
		return demoTenantIds.contains(tenant.getId()) ? AdminTenantData.TYPE_DEMO : AdminTenantData.TYPE_CUSTOMER;
	}

	public static List<ObjectId> objectIds(Collection<String> ids) {
		return ids.stream().filter(ObjectId::isValid).map(ObjectId::new).toList();
	}
}
