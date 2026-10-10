package ai.qorva.core.admin.service;

import ai.qorva.core.admin.config.AdminProperties;
import ai.qorva.core.admin.dto.AdminPage;
import ai.qorva.core.admin.dto.AdminTesterData;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.ProductReferenceDTO;
import ai.qorva.core.dto.common.StripePrice;
import ai.qorva.core.enums.TenantAccountTypeEnum;
import ai.qorva.core.enums.TenantStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import ai.qorva.core.utils.Paging;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Test accounts and the tiers they can be given. Read-only. */
@Service
public class AdminTesterQueries {

	private final AdminTenantSupport support;
	private final ProductReferenceService products;
	private final AdminProperties properties;

	public AdminTesterQueries(AdminTenantSupport support, ProductReferenceService products, AdminProperties properties) {
		this.support = support;
		this.products = products;
		this.properties = properties;
	}

	public AdminPage<AdminTesterData.Tester> list(Map<String, String> params) {
		int page = Paging.page(Paging.param(params, "page", 0));
		int size = Paging.size(Paging.param(params, "size", 25));
		var criteria = new ArrayList<Criteria>();
		criteria.add(Criteria.where("accountType").is(TenantAccountTypeEnum.TESTER.name()));
		var q = params.get("q");
		if (StringUtils.hasText(q)) {
			var pattern = Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
			criteria.add(new Criteria().orOperator(Criteria.where("tenantName").regex(pattern), Criteria.where("contactEmail").regex(pattern)));
		}
		var state = params.get("state");
		if (StringUtils.hasText(state)) {
			criteria.add(stateCriteria(state, Instant.now()));
		}
		var query = Query.query(new Criteria().andOperator(criteria));
		long total = support.mongo().count(query, Tenant.class);
		query.with(Sort.by(Sort.Order.asc("accessExpiresAt"), Sort.Order.asc("_id"))).skip((long) page * size).limit(size);
		var rows = support.mongo().find(query, Tenant.class).stream().map(this::view).toList();
		return AdminPage.of(rows, page, size, total);
	}

	public AdminTesterData.Tester get(String tenantId) throws QorvaException {
		var tenant = support.requireTenant(tenantId);
		if (!AdminTenantService.isTester(tenant)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_NOT_A_TESTER);
		}
		return view(tenant);
	}

	public List<AdminTesterData.Tier> tiers() {
		return products.findAllActive().stream()
			.map(p -> monthlyPrice(p).map(price -> tier(p, price.getStripePriceId())).orElse(null))
			.filter(java.util.Objects::nonNull)
			.sorted(Comparator.comparing(t -> t.limits() != null && t.limits().screeningActions() != null ? t.limits().screeningActions() : 0))
			.toList();
	}

	/** The product and its monthly price, or 400 when the product is unknown, inactive or has no monthly price. */
	public ResolvedTier resolve(String productId) throws QorvaException {
		if (!StringUtils.hasText(productId)) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_UNKNOWN_TIER);
		}
		var product = products.findByStripeProductId(productId);
		if (product == null || !product.isActive()) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_UNKNOWN_TIER);
		}
		var price = monthlyPrice(product).orElseThrow(() -> QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_UNKNOWN_TIER));
		return new ResolvedTier(product, price.getStripePriceId());
	}

	public record ResolvedTier(ProductReferenceDTO product, String priceId) {}

	/** The product behind a stored price id (a tester's current tier). */
	public ResolvedTier resolveByPrice(String priceId) throws QorvaException {
		var product = products.findByStripePriceId(priceId);
		if (product == null) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_UNKNOWN_TIER);
		}
		return new ResolvedTier(product, priceId);
	}

	AdminTesterData.Tester view(Tenant tenant) {
		var owner = support.usersOf(tenant.getId()).stream().findFirst().orElse(null);
		var sub = tenant.getSubscriptionInfo();
		AdminTesterData.TierRef tier = null;
		if (sub != null && StringUtils.hasText(sub.getPriceId())) {
			ProductReferenceDTO product = null;
			try {
				product = products.findByStripePriceId(sub.getPriceId());
			} catch (QorvaException ignored) {
				// shown without a product id
			}
			tier = new AdminTesterData.TierRef(product != null ? product.getStripeProductId() : null, sub.getSubscriptionPlan(), sub.getPriceId());
		}
		return new AdminTesterData.Tester(tenant.getId(), tenant.getTenantName(), owner != null ? owner.getId() : null,
			owner != null ? owner.getFirstName() : null, owner != null ? owner.getLastName() : null,
			owner != null ? owner.getEmail() : tenant.getContactEmail(), owner != null ? owner.getCommunicationLanguage() : null,
			tier, tenant.getAccessExpiresAt(), TenantStatusEnum.of(tenant.getStatus()).name(),
			state(tenant, Instant.now()), tenant.getCreatedAt());
	}

	String state(Tenant tenant, Instant now) {
		return switch (TenantStatusEnum.of(tenant.getStatus())) {
			case DELETED -> AdminTesterData.STATE_DELETED;
			case SUSPENDED -> AdminTesterData.STATE_DEACTIVATED;
			case ACTIVE -> {
				var end = tenant.getAccessExpiresAt();
				if (end != null && !now.isBefore(end)) yield AdminTesterData.STATE_EXPIRED;
				if (end != null && now.plus(properties.getExpiringSoon()).isAfter(end)) yield AdminTesterData.STATE_EXPIRING_SOON;
				yield AdminTesterData.STATE_ACTIVE;
			}
		};
	}

	private Criteria stateCriteria(String state, Instant now) {
		var active = new Criteria().orOperator(Criteria.where("status").is(TenantStatusEnum.ACTIVE.name()),
			Criteria.where("status").exists(false), Criteria.where("status").is(null));
		var soon = now.plus(properties.getExpiringSoon());
		return switch (state) {
			case AdminTesterData.STATE_DELETED -> Criteria.where("status").is(TenantStatusEnum.DELETED.name());
			case AdminTesterData.STATE_DEACTIVATED -> Criteria.where("status").is(TenantStatusEnum.SUSPENDED.name());
			case AdminTesterData.STATE_EXPIRED -> new Criteria().andOperator(active, Criteria.where("accessExpiresAt").lte(now));
			case AdminTesterData.STATE_EXPIRING_SOON -> new Criteria().andOperator(active, Criteria.where("accessExpiresAt").gt(now).lte(soon));
			default -> new Criteria().andOperator(active, new Criteria().orOperator(
				Criteria.where("accessExpiresAt").gt(soon), Criteria.where("accessExpiresAt").is(null)));
		};
	}

	private static Optional<StripePrice> monthlyPrice(ProductReferenceDTO product) {
		return product.getPrices() == null ? Optional.empty() : product.getPrices().stream()
			.filter(StripePrice::isActive)
			.filter(p -> "month".equals(p.getInterval()) && (p.getIntervalCount() == null || p.getIntervalCount() == 1))
			.findFirst();
	}

	private static AdminTesterData.Tier tier(ProductReferenceDTO p, String priceId) {
		var f = p.getFeatures();
		var l = f != null ? f.getLimits() : null;
		var limits = l == null ? null : new AdminTesterData.TierLimits(l.getScreeningActions(), l.getAiResumeChats(),
			l.getTalentIntelligenceQueries(), l.getAgentRuns(), l.getEmailTemplates(), l.getBulkUploadFiles(), l.getAtsConnections(),
			l.getMatchingTopNMax(), l.getMatchingTopNDefault());
		return new AdminTesterData.Tier(p.getStripeProductId(), p.getName(), priceId, f != null ? f.getSeats() : null, limits);
	}

	static User owner(AdminTenantSupport support, String tenantId) throws QorvaException {
		return support.usersOf(tenantId).stream().findFirst()
			.orElseThrow(() -> QorvaErrors.notFound(QorvaErrorCodes.ADMIN_USER_NOT_FOUND));
	}
}
