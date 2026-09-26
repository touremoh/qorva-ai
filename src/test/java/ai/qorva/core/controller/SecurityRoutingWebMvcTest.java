package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.config.SecurityConfig;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.service.ATSExportService;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.InsightConversationService;
import ai.qorva.core.service.JobDescriptionBuilderService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.LibraryClearService;
import ai.qorva.core.service.LibraryInsightsService;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.service.QorvaApiAccessManager;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.service.S3StorageService;
import ai.qorva.core.service.ScoringRulesPrefillService;
import ai.qorva.core.service.StripeEventsService;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UserService;
import ai.qorva.core.utils.JwtUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the web layer with the real security chain and JWT filter for the controllers the
 * 2026-09-26 security hotfix touched: proves the routes map without conflicts, that the public
 * surface is only the webhook and checkout pages, and that the CrudPolicy answers over HTTP.
 */
@WebMvcTest(controllers = {
	UserController.class, TenantController.class, StripeController.class,
	CVController.class, JobPostController.class, MatchingReportController.class, LibraryInsightsController.class
})
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
	"weblink.allowedOrigins=http://localhost:5173",
	"stripe.webhook.secret=whsec_test",
	"jwt.secret=" + SecurityRoutingWebMvcTest.SECRET
})
class SecurityRoutingWebMvcTest {

	static final String SECRET = "cXVvcnZhLXRlc3Qtc2VjcmV0LXF1b3J2YS10ZXN0LXNlY3JldC1xdW9ydmEtdGVzdC1zZWNyZXQtcXVvcnZhLXRlc3Q=";
	private static final String ME = "me@acme.test";
	private static final String TENANT = new ObjectId().toHexString();
	private static final String USER_ID = new ObjectId().toHexString();

	@Autowired private MockMvc mvc;
	@Autowired private JwtConfig jwtConfig;

	@MockitoBean private QorvaUserDetailsService userDetailsService;
	@MockitoBean(name = "accessManager") private QorvaApiAccessManager accessManager;
	@MockitoBean private UserService userService;
	@MockitoBean private TenantService tenantService;
	@MockitoBean private S3StorageService s3StorageService;
	@MockitoBean private StripeEventsService stripeEventsService;
	@MockitoBean private CVService cvService;
	@MockitoBean private LibraryClearService libraryClearService;
	@MockitoBean private JobPostService jobPostService;
	@MockitoBean private ScoringRulesPrefillService scoringRulesPrefillService;
	@MockitoBean private JobDescriptionBuilderService jobDescriptionBuilderService;
	@MockitoBean private MatchingReportService matchingReportService;
	@MockitoBean private ATSExportService atsExportService;
	@MockitoBean private LibraryInsightsService libraryInsightsService;
	@MockitoBean private InsightConversationService insightConversationService;

	private String token;

	@BeforeEach
	void setUp() {
		UserDetails me = User.withUsername(ME).password("x").authorities(List.of()).build();
		when(userDetailsService.loadUserByUsername(ME)).thenReturn(me);
		var tenant = new TenantDTO();
		tenant.setId(TENANT);
		token = "Bearer " + JwtUtils.generateToken(me, jwtConfig, tenant);
	}

	@Test
	void secretIsLongEnough() {
		assertThat(Base64.getDecoder().decode(SECRET)).hasSizeGreaterThanOrEqualTo(64);
	}

	@Test
	void stripeGenericCrud_isNotReachable_anonymously() throws Exception {
		for (var request : List.of(get("/stripe"), get("/stripe/" + USER_ID), post("/stripe/ids"),
			put("/stripe/" + USER_ID), delete("/stripe/" + USER_ID))) {
			var status = mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andReturn().getResponse().getStatus();
			assertThat(status).as(request.toString()).isIn(401, 403, 404, 405);
		}
		verifyNoInteractions(stripeEventsService);
	}

	@Test
	void stripeCheckoutPages_stayPublic() throws Exception {
		mvc.perform(get("/stripe/checkout/cancel")).andExpect(status().isOk());
		mvc.perform(get("/stripe/checkout/success").param("session_id", "cs_test")).andExpect(status().isOk());
	}

	@Test
	void stripePortalSession_needsAToken() throws Exception {
		var status = mvc.perform(post("/stripe/portal-session")).andReturn().getResponse().getStatus();
		assertThat(status).isIn(401, 403);
		verify(stripeEventsService, never()).buildStripePortalSessionUrl(any());
	}

	@Test
	void tenantsList_isClosed() throws Exception {
		mvc.perform(get("/tenants").header("Authorization", token)).andExpect(status().isNotFound());
		verifyNoInteractions(tenantService);
	}

	@Test
	void ownTenant_isReadable() throws Exception {
		when(tenantService.findOneById(TENANT)).thenReturn(new TenantDTO());
		mvc.perform(get("/tenants/" + TENANT).header("Authorization", token)).andExpect(status().isOk());
	}

	@Test
	void cvList_withoutViewCv_isForbidden() throws Exception {
		when(accessManager.hasPermission(any(), anyString())).thenReturn(false);
		mvc.perform(get("/cvs").header("Authorization", token)).andExpect(status().isForbidden());
		verifyNoInteractions(cvService);
	}

	@Test
	void libraryInsights_withoutPermission_isForbidden() throws Exception {
		when(accessManager.hasPermission(any(), eq("VIEW_LIBRARY_INSIGHTS"))).thenReturn(false);
		mvc.perform(get("/library-insights/conversations").header("Authorization", token)).andExpect(status().isForbidden());
		verifyNoInteractions(insightConversationService);
	}

	@Test
	void userProfilePatch_onSelf_passesOnlyTheNames() throws Exception {
		var self = new UserDTO();
		self.setEmail(ME);
		when(userService.findOneById(USER_ID)).thenReturn(self);
		var written = ArgumentCaptor.forClass(UserDTO.class);
		when(userService.updateOne(eq(USER_ID), written.capture())).thenReturn(new UserDTO());

		mvc.perform(patch("/users/" + USER_ID).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"id":"%s","firstName":"Ada","lastName":"Lovelace",
					 "authorities":[{"role":"ACCOUNT_OWNER","action":"MANAGE_USERS","permission":"ALLOWED"}],
					 "encryptedPassword":"$forged","passwordCredentialVersion":99,"tenantId":"%s"}
					""".formatted(new ObjectId().toHexString(), new ObjectId().toHexString())))
			.andExpect(status().isOk());

		assertThat(written.getValue().getFirstName()).isEqualTo("Ada");
		assertThat(written.getValue().getAuthorities()).isNull();
		assertThat(written.getValue().getEncryptedPassword()).isNull();
		assertThat(written.getValue().getTenantId()).isNull();
	}

	@Test
	void userDelete_withoutManageUsers_isForbidden() throws Exception {
		when(accessManager.hasPermission(any(), eq("MANAGE_USERS"))).thenReturn(false);
		mvc.perform(delete("/users/" + USER_ID).header("Authorization", token)).andExpect(status().isForbidden());
		verify(userService, never()).deleteOneById(any(), any());
	}
}
