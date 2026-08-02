package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CandidateEmailTemplate;
import ai.qorva.core.dao.repository.CandidateEmailTemplateRepository;
import ai.qorva.core.dto.CandidateEmailTemplateData;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateEmailTemplateServiceTest {

	private static final String TENANT = "tenant-1";

	@Mock
	private CandidateEmailTemplateRepository repository;

	@Mock
	private CandidateUpdateEmailService emailService;

	@Mock
	private TenantService tenantService;

	@Mock
	private UserService userService;

	@Mock
	private ProductReferenceService productReferenceService;

	private CandidateEmailTemplateService service;

	@BeforeEach
	void setUp() {
		service = new CandidateEmailTemplateService(repository, emailService, tenantService, userService, productReferenceService);
	}

	private CandidateEmailTemplateData.SaveRequest request(String name, String subject, String body) {
		return new CandidateEmailTemplateData.SaveRequest(name, subject, body);
	}

	@Test
	void create_valid_savesTrimmedTemplate() throws Exception {
		when(repository.existsByTenantIdAndName(TENANT, "Friendly")).thenReturn(false);
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.create(TENANT, "alice@acme.com",
			request(" Friendly ", "Hello {{candidate_name}}", "Body from {{company_name}}"));

		assertThat(view.name()).isEqualTo("Friendly");
		assertThat(view.createdBy()).isEqualTo("alice@acme.com");
		verify(repository).save(any(CandidateEmailTemplate.class));
	}

	@Test
	void create_unknownPlaceholder_isRejected() {
		assertThatThrownBy(() -> service.create(TENANT, "a@b.c",
			request("Name", "Subject", "Hi {{candidat_name}}")))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("Unknown placeholder");
		verify(repository, never()).save(any());
	}

	@Test
	void create_duplicateName_isRejected() {
		when(repository.existsByTenantIdAndName(TENANT, "Name")).thenReturn(true);

		assertThatThrownBy(() -> service.create(TENANT, "a@b.c",
			request("Name", "Subject", "Body")))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("already exists");
	}

	@Test
	void create_missingFields_areRejected() {
		assertThatThrownBy(() -> service.create(TENANT, "a@b.c", request(" ", "Subject", "Body")))
			.isInstanceOf(QorvaException.class);
		assertThatThrownBy(() -> service.create(TENANT, "a@b.c", request("Name", " ", "Body")))
			.isInstanceOf(QorvaException.class);
		assertThatThrownBy(() -> service.create(TENANT, "a@b.c", request("Name", "Subject", " ")))
			.isInstanceOf(QorvaException.class);
	}

	@Test
	void create_oversizedContent_isRejected() {
		assertThatThrownBy(() -> service.create(TENANT, "a@b.c",
			request("Name", "s".repeat(151), "Body")))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("150");
		assertThatThrownBy(() -> service.create(TENANT, "a@b.c",
			request("Name", "Subject", "b".repeat(4001))))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("4000");
	}

	@Test
	void update_renameToExistingName_isRejected() {
		var existing = CandidateEmailTemplate.builder().id("t1").tenantId(TENANT).name("Old").build();
		when(repository.findByIdAndTenantId("t1", TENANT)).thenReturn(Optional.of(existing));
		when(repository.existsByTenantIdAndName(TENANT, "Taken")).thenReturn(true);

		assertThatThrownBy(() -> service.update(TENANT, "t1",
			request("Taken", "Subject", "Body")))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("already exists");
	}

	@Test
	void update_keepingOwnName_isAllowed() throws Exception {
		var existing = CandidateEmailTemplate.builder().id("t1").tenantId(TENANT).name("Mine").build();
		when(repository.findByIdAndTenantId("t1", TENANT)).thenReturn(Optional.of(existing));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.update(TENANT, "t1", request("Mine", "New subject", "New body"));

		assertThat(view.subject()).isEqualTo("New subject");
		verify(repository, never()).existsByTenantIdAndName(anyString(), anyString());
	}

	@Test
	void create_overPlanLimit_isRejected() throws Exception {
		stubPlanWithTemplateLimit(3);
		when(repository.countByTenantId(TENANT)).thenReturn(3L);

		assertThatThrownBy(() -> service.create(TENANT, "a@b.c",
			request("Name", "Subject", "Body")))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("up to 3");
		verify(repository, never()).save(any());
	}

	@Test
	void create_underPlanLimit_isAllowed() throws Exception {
		stubPlanWithTemplateLimit(3);
		when(repository.countByTenantId(TENANT)).thenReturn(2L);
		when(repository.existsByTenantIdAndName(TENANT, "Name")).thenReturn(false);
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.create(TENANT, "a@b.c", request("Name", "Subject", "Body"));

		assertThat(view.name()).isEqualTo("Name");
	}

	@Test
	void create_unresolvablePlan_isTreatedAsUnlimited() throws Exception {
		when(tenantService.findOneById(TENANT)).thenThrow(new QorvaException("boom"));
		when(repository.existsByTenantIdAndName(TENANT, "Name")).thenReturn(false);
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.create(TENANT, "a@b.c", request("Name", "Subject", "Body"));

		assertThat(view.name()).isEqualTo("Name");
	}

	private void stubPlanWithTemplateLimit(int limit) throws Exception {
		var sub = new ai.qorva.core.dto.common.SubscriptionInfo();
		sub.setPriceId("price_1");
		var tenant = new ai.qorva.core.dto.TenantDTO();
		tenant.setSubscriptionInfo(sub);
		when(tenantService.findOneById(TENANT)).thenReturn(tenant);
		when(productReferenceService.findByStripePriceId("price_1")).thenReturn(
			ai.qorva.core.dto.ProductReferenceDTO.builder()
				.features(ai.qorva.core.dto.common.ProductFeatures.builder()
					.limits(ai.qorva.core.dto.common.FeatureLimits.builder().emailTemplates(limit).build())
					.build())
				.build());
	}

	@Test
	void findOwned_otherTenant_throwsNotFound() {
		when(repository.findByIdAndTenantId("t1", TENANT)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findOwned(TENANT, "t1"))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("not found");
	}
}
