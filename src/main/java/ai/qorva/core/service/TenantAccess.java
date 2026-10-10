package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.enums.TenantStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether a company may be used at all: status ACTIVE (absent counts as ACTIVE) and, for a test account, before its
 * access end. One rule for sign-in, the workers, the ATS webhooks, the candidate links and the OAuth callbacks.
 * Workers read a per-instance snapshot of the unusable tenants, refreshed every {@link #SNAPSHOT_TTL}; the admin
 * console evicts it on every change, and sign-in always reads the tenant itself.
 */
@Service
public class TenantAccess {

	static final Duration SNAPSHOT_TTL = Duration.ofSeconds(30);
	private static final String KEY = "unusable";

	private final MongoTemplate mongoTemplate;
	private final Cache<String, Set<String>> snapshot = Caffeine.newBuilder().expireAfterWrite(SNAPSHOT_TTL).build();

	public TenantAccess(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/** The error a sign-in gets for this tenant, or empty when it may be used. */
	public static Optional<String> refusal(String status, Instant accessExpiresAt, Instant now) {
		return switch (TenantStatusEnum.of(status)) {
			case SUSPENDED -> Optional.of(QorvaErrorCodes.AUTH_TENANT_SUSPENDED);
			case DELETED -> Optional.of(QorvaErrorCodes.AUTH_TENANT_DELETED);
			case ACTIVE -> accessExpiresAt != null && !now.isBefore(accessExpiresAt)
				? Optional.of(QorvaErrorCodes.AUTH_ACCOUNT_EXPIRED) : Optional.empty();
		};
	}

	public static Optional<String> refusal(TenantDTO tenant) {
		return tenant == null ? Optional.empty() : refusal(tenant.getStatus(), tenant.getAccessExpiresAt(), Instant.now());
	}

	public static Optional<String> refusal(Tenant tenant) {
		return tenant == null ? Optional.empty() : refusal(tenant.getStatus(), tenant.getAccessExpiresAt(), Instant.now());
	}

	/** 403 with the reason, for the sign-in paths. */
	public static void assertUsable(TenantDTO tenant) throws QorvaException {
		var refusal = refusal(tenant);
		if (refusal.isPresent()) {
			throw QorvaErrors.forbidden(refusal.get());
		}
	}

	/** For background work and public entry points; may lag an admin change on another instance by {@link #SNAPSHOT_TTL}. */
	public boolean isUsable(String tenantId) {
		return tenantId == null || !unusableTenantIds().contains(tenantId);
	}

	public Set<String> unusableTenantIds() {
		return snapshot.get(KEY, k -> load());
	}

	/** {@code field ∉ unusable tenants}, for claim queries; the ids are stored as ObjectIds. */
	public Criteria usableTenantsOnly(String field) {
		return Criteria.where(field).nin(unusableTenantIds().stream().filter(ObjectId::isValid).map(ObjectId::new).toList());
	}

	public void evict() {
		snapshot.invalidateAll();
	}

	private Set<String> load() {
		var query = Query.query(new Criteria().orOperator(
			Criteria.where("status").in(TenantStatusEnum.SUSPENDED.name(), TenantStatusEnum.DELETED.name()),
			Criteria.where("accessExpiresAt").lte(Instant.now())));
		query.fields().include("_id");
		return mongoTemplate.find(query, Tenant.class).stream().map(Tenant::getId).collect(Collectors.toUnmodifiableSet());
	}
}
