package ai.qorva.core.service;

import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dto.common.FeatureLimits;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * How many candidates one matching run may report per job — the plan's Top N. Choices go in steps of
 * {@link #STEP} up to the plan's maximum; every path that starts matching (the run endpoint, the deprecated
 * one-click run, Copilot) validates through here, so the cap holds whatever the caller.
 * <p>
 * Resolved like the other static caps: the Stripe price's product reference, then the plan configuration
 * matched on the product name, then the Starter configuration — a workspace with no plan gets Starter's.
 */
@Slf4j
@Service
public class MatchingTopNPolicy {

	public static final int STEP = 5;

	/** Last resort when not even the Starter configuration carries a value — today's behaviour. */
	static final int FALLBACK_MAX = 10;
	static final int FALLBACK_DEFAULT = 10;

	private final TenantService tenantService;
	private final ProductReferenceService productReferenceService;
	private final QorvaProductProperties productProperties;

	public MatchingTopNPolicy(TenantService tenantService, ProductReferenceService productReferenceService,
	                          QorvaProductProperties productProperties) {
		this.tenantService = tenantService;
		this.productReferenceService = productReferenceService;
		this.productProperties = productProperties;
	}

	/** The plan's cap and default; {@link #allowed()} is what the run dialog offers. */
	public record Limits(int max, int defaultTopN) {

		public Limits {
			max = Math.max(STEP, max - max % STEP);
			defaultTopN = Math.min(max, Math.max(STEP, defaultTopN - defaultTopN % STEP));
		}

		public List<Integer> allowed() {
			return IntStream.rangeClosed(1, max / STEP).map(i -> i * STEP).boxed().toList();
		}

		/** A job's saved choice, clamped to the current plan (it may have been downgraded since); null → default. */
		public int clamp(Integer saved) {
			if (saved == null || saved < STEP) {
				return defaultTopN;
			}
			return Math.min(max, saved - saved % STEP);
		}
	}

	public Limits limitsFor(String tenantId) {
		var limits = planLimits(tenantId);
		var starter = configured(productProperties.getStarter());
		int max = firstNonNull(limits != null ? limits.getMatchingTopNMax() : null,
			starter != null ? starter.getMatchingTopNMax() : null, FALLBACK_MAX);
		int defaultTopN = firstNonNull(limits != null ? limits.getMatchingTopNDefault() : null,
			starter != null ? starter.getMatchingTopNDefault() : null, FALLBACK_DEFAULT);
		return new Limits(max, defaultTopN);
	}

	/**
	 * The Top N to run with: {@code requested} when it is a step of 5 within the plan, the job's saved
	 * value or the plan default when null. 400 for a value off the steps, 403 for one above the plan.
	 */
	public int resolve(String tenantId, Integer requested, Integer savedForJob) throws QorvaException {
		var limits = limitsFor(tenantId);
		if (requested == null) {
			return limits.clamp(savedForJob);
		}
		if (requested < STEP || requested % STEP != 0) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.MATCHING_TOP_N_INVALID, limits.max());
		}
		if (requested > limits.max()) {
			throw QorvaErrors.forbidden(QorvaErrorCodes.MATCHING_TOP_N_NOT_IN_PLAN, limits.max());
		}
		return requested;
	}

	private FeatureLimits planLimits(String tenantId) {
		try {
			var sub = tenantService.findOneById(tenantId).getSubscriptionInfo();
			if (sub == null) {
				return null;
			}
			if (StringUtils.hasText(sub.getPriceId())) {
				var product = productReferenceService.findByStripePriceId(sub.getPriceId());
				if (product != null && product.getFeatures() != null && product.getFeatures().getLimits() != null
					&& product.getFeatures().getLimits().getMatchingTopNMax() != null) {
					return product.getFeatures().getLimits();
				}
			}
			return configuredFor(sub.getSubscriptionPlan());
		} catch (Exception e) {
			log.warn("Could not resolve matching Top N for tenant {}: {}", tenantId, e.getMessage());
			return null;
		}
	}

	/** Plan limits straight from configuration, matched on the Stripe product name. */
	private FeatureLimits configuredFor(String subscriptionPlan) {
		if (!StringUtils.hasText(subscriptionPlan)) {
			return null;
		}
		return Stream.of(productProperties.getStarter(), productProperties.getPro(), productProperties.getScale())
			.filter(plan -> plan != null && plan.getStripeProductName() != null
				&& plan.getStripeProductName().equalsIgnoreCase(subscriptionPlan.trim()))
			.map(MatchingTopNPolicy::configured)
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	private static FeatureLimits configured(QorvaProductProperties.ProductPlanConfig plan) {
		return plan != null && plan.getFeatures() != null ? plan.getFeatures().getLimits() : null;
	}

	private static int firstNonNull(Integer first, Integer second, int fallback) {
		return first != null ? first : second != null ? second : fallback;
	}
}
