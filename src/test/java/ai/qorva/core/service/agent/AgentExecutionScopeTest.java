package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.security.LanguageContextHolder;
import ai.qorva.core.security.QorvaUserDetails;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.QorvaApiAccessManager;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentExecutionScopeTest {

	private static final String TENANT = "64b7f0f0f0f0f0f0f0f0f0f0";
	private static final String EMAIL = "owner@a.test";

	@Mock private QorvaUserDetailsService userDetailsService;
	@Mock private TenantService tenantService;

	private AgentExecutionScope scope;

	@BeforeEach
	void setUp() throws Exception {
		scope = new AgentExecutionScope(userDetailsService, tenantService, new QorvaApiAccessManager(null, null));
		when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(user(true, true, TENANT, "USE_AGENT:ALLOWED", "VIEW_CV:ALLOWED"));
		when(tenantService.findOneById(anyString())).thenReturn(tenant("active"));
	}

	@AfterEach
	void clean() {
		SecurityContextHolder.clearContext();
		LanguageContextHolder.clear();
	}

	private static QorvaUserDetails user(boolean enabled, boolean nonLocked, String tenantId, String... authorities) {
		return new QorvaUserDetails(EMAIL, "x", enabled, true, nonLocked,
			List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList(), tenantId, 1);
	}

	private static TenantDTO tenant(String status) {
		var tenant = new TenantDTO();
		var info = new SubscriptionInfo();
		info.setSubscriptionStatus(status);
		tenant.setSubscriptionInfo(info);
		return tenant;
	}

	private static AgentRun run() {
		var run = new AgentRun();
		run.setTenantId(TENANT);
		run.setUserEmail(EMAIL);
		run.setLanguage("fr");
		return run;
	}

	@Test
	void runsAsTheUserInTheTenantAndLanguageThenClearsEverything() throws Exception {
		var seen = new AtomicReference<String>();

		var result = scope.call(run(), ctx -> {
			seen.set(TenantContextHolder.getTenantId() + "|" + SecurityContextHolder.getContext().getAuthentication().getName()
				+ "|" + LanguageContextHolder.getLanguage() + "|" + ctx.userEmail());
			return "done";
		});

		assertThat(result).isEqualTo("done");
		assertThat(seen.get()).isEqualTo(TENANT + "|" + EMAIL + "|fr|" + EMAIL);
		assertThat(TenantContextHolder.getTenantId()).isNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(LanguageContextHolder.getLanguage()).isEqualTo("en");
	}

	@Test
	void clearsEverythingWhenTheWorkThrows() {
		assertThatThrownBy(() -> scope.call(run(), ctx -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(TenantContextHolder.getTenantId()).isNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void aUserWithoutUseAgentCannotAct() {
		when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(user(true, true, TENANT, "VIEW_CV:ALLOWED"));

		assertThatThrownBy(() -> scope.call(run(), ctx -> "never"))
			.isInstanceOfSatisfying(AgentPreconditionException.class,
				e -> assertThat(e.reason()).isEqualTo(QorvaErrorCodes.AGENT_USER_UNAVAILABLE));
	}

	@Test
	void aLockedOrDeletedOrMovedUserCannotAct() {
		when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(user(true, false, TENANT, "USE_AGENT:ALLOWED"));
		assertThatThrownBy(() -> scope.call(run(), ctx -> "never")).isInstanceOf(AgentPreconditionException.class);

		when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(user(true, true, "64b7f0f0f0f0f0f0f0f0f0ff", "USE_AGENT:ALLOWED"));
		assertThatThrownBy(() -> scope.call(run(), ctx -> "never")).isInstanceOf(AgentPreconditionException.class);

		when(userDetailsService.loadUserByUsername(EMAIL)).thenThrow(new UsernameNotFoundException(EMAIL));
		assertThatThrownBy(() -> scope.call(run(), ctx -> "never")).isInstanceOf(AgentPreconditionException.class);
	}

	@Test
	void aBlockedSubscriptionStopsTheRun() throws Exception {
		when(tenantService.findOneById(anyString())).thenReturn(tenant("canceled"));

		assertThatThrownBy(() -> scope.call(run(), ctx -> "never"))
			.isInstanceOfSatisfying(AgentPreconditionException.class,
				e -> assertThat(e.reason()).isEqualTo(QorvaErrorCodes.AUTH_SUBSCRIPTION_INACTIVE));
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}
