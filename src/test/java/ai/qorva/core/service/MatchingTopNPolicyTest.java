package ai.qorva.core.service;

import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.common.FeatureLimits;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Top N per plan: steps of 5 up to the plan's cap, the plan default when nothing is chosen, Starter's without a plan. */
@ExtendWith(MockitoExtension.class)
class MatchingTopNPolicyTest {

	private static final String TENANT = "t1";

	@Mock private TenantService tenantService;
	@Mock private ProductReferenceService productReferenceService;

	private MatchingTopNPolicy policy;

	private static QorvaProductProperties.ProductPlanConfig plan(String name, int max) {
		var limits = FeatureLimits.builder().matchingTopNMax(max).matchingTopNDefault(10).build();
		return new QorvaProductProperties.ProductPlanConfig(name, ProductFeatures.builder().limits(limits).build());
	}

	@BeforeEach
	void setUp() {
		var properties = new QorvaProductProperties(plan("Starter", 10), plan("Pro", 20), plan("Scale", 30));
		policy = new MatchingTopNPolicy(tenantService, productReferenceService, properties);
	}

	private void onPlan(String plan) throws QorvaException {
		var sub = new SubscriptionInfo();
		sub.setSubscriptionPlan(plan);
		var tenant = new TenantDTO();
		tenant.setSubscriptionInfo(sub);
		when(tenantService.findOneById(TENANT)).thenReturn(tenant);
	}

	@Test
	void eachPlanOffersStepsOfFiveUpToItsCapWithTenAsTheDefault() throws Exception {
		onPlan("Pro");

		var limits = policy.limitsFor(TENANT);

		assertThat(limits.allowed()).containsExactly(5, 10, 15, 20);
		assertThat(limits.defaultTopN()).isEqualTo(10);
	}

	@Test
	void scaleGoesUpToThirty() throws Exception {
		onPlan("Scale");

		assertThat(policy.limitsFor(TENANT).allowed()).containsExactly(5, 10, 15, 20, 25, 30);
	}

	@Test
	void aWorkspaceWithoutAPlanGetsStartersCap() throws Exception {
		when(tenantService.findOneById(TENANT)).thenReturn(new TenantDTO());

		assertThat(policy.limitsFor(TENANT).max()).isEqualTo(10);
	}

	@Test
	void nothingChosenMeansTheJobsLastChoiceClampedToThePlanOrTheDefault() throws Exception {
		onPlan("Starter");

		assertThat(policy.resolve(TENANT, null, null)).isEqualTo(10);
		assertThat(policy.resolve(TENANT, null, 5)).isEqualTo(5);
		// Saved on Scale, now on Starter after a downgrade.
		assertThat(policy.resolve(TENANT, null, 30)).isEqualTo(10);
	}

	@Test
	void aValueOffTheStepsIsABadRequestAndOneAboveThePlanIsForbidden() throws Exception {
		onPlan("Pro");

		assertThat(policy.resolve(TENANT, 15, null)).isEqualTo(15);
		assertThatThrownBy(() -> policy.resolve(TENANT, 12, null))
			.isInstanceOfSatisfying(QorvaException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		assertThatThrownBy(() -> policy.resolve(TENANT, 25, null))
			.isInstanceOfSatisfying(QorvaException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
	}
}
