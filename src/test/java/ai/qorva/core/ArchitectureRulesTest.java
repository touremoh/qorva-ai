package ai.qorva.core;

import ai.qorva.core.controller.AbstractQorvaController;
import ai.qorva.core.security.TenantContextHolder;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural rules the refactor must keep true: who may switch the tenant, which way the layers
 * depend, and where tenant-blind data access is still allowed (an explicit, reviewed list).
 */
class ArchitectureRulesTest {

	private static JavaClasses production;

	@BeforeAll
	static void importClasses() {
		production = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("ai.qorva.core");
	}

	/**
	 * Only the JWT filter (requests) and TenantScope (everything else, with restore) set or clear the
	 * tenant; nothing may switch it ad hoc.
	 */
	@Test
	void onlyTheJwtFilterAndTenantScopeWriteTheTenantContext() {
		noClasses().that().doNotHaveSimpleName("JwtRequestFilter").and().doNotHaveSimpleName("TenantScope")
			.should().callMethod(TenantContextHolder.class, "setTenantId", String.class)
			.orShould().callMethod(TenantContextHolder.class, "clear")
			.check(production);
	}

	/**
	 * A load/delete by bare id ignores the tenant. Tenant-aware code uses {@code findByIdInTenant} or the
	 * generic service; the bare form is left to the base class and to the flows that learn the tenant
	 * from the document itself (sign-in challenges, set-password links, a webhook naming its
	 * connection, the Stripe checkout binding, which checks the user against the session's tenant) and
	 * to the global Stripe webhook ledger, keyed by Stripe's event id.
	 */
	@Test
	void tenantBlindByIdAccessOnlyInReviewedPlaces() {
		var byIdMethods = Set.of("findById", "existsById", "deleteById", "findAllById", "deleteAllById");
		var onARepository = new DescribedPredicate<JavaMethodCall>("a by-id call on a repository") {
			@Override
			public boolean test(JavaMethodCall call) {
				return byIdMethods.contains(call.getName())
					&& call.getTargetOwner().getPackageName().startsWith("ai.qorva.core.dao.repository");
			}
		};
		noClasses().that().doNotHaveSimpleName("AbstractQorvaService")
			.and().doNotHaveSimpleName("MfaService")
			.and().doNotHaveSimpleName("SetPasswordService")
			.and().doNotHaveSimpleName("AtsWebhookReceiver")
			.and().doNotHaveSimpleName("StripeCheckoutSessionCompletedHandler")
			.and().doNotHaveSimpleName("StripeEventDispatcher")
			.should().callMethodWhere(onARepository)
			.check(production);
	}

	/** Persistence never reaches up into the web or service layers. */
	@Test
	void daoDoesNotDependOnServicesOrControllers() {
		noClasses().that().resideInAPackage("ai.qorva.core.dao..")
			.should().dependOnClassesThat().resideInAnyPackage("ai.qorva.core.service..", "ai.qorva.core.controller..")
			.check(production);
	}

	/** Controllers go through services, never straight to a repository. */
	@Test
	void controllersDoNotUseRepositories() {
		noClasses().that().resideInAPackage("ai.qorva.core.controller..")
			.should().dependOnClassesThat().resideInAPackage("ai.qorva.core.dao.repository..")
			.check(production);
	}

	/** A generic CRUD controller is always a real REST controller (so its CrudPolicy is what guards it). */
	@Test
	void crudControllersAreRestControllers() {
		classes().that().areAssignableTo(AbstractQorvaController.class).and().doNotHaveSimpleName("AbstractQorvaController")
			.should().beAnnotatedWith(RestController.class)
			.check(production);
	}

	/**
	 * Tenant-wide deletes happen in one place, the cascade package (CascadeRegistry#purgeTenant), so
	 * what "clear the library" and "purge a demo" remove can no longer drift apart.
	 */
	@Test
	void tenantWideDeletesOnlyInTheCascadeRegistry() {
		noClasses().that().resideOutsideOfPackage("ai.qorva.core.service.cascade..")
			.should().callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
				com.tngtech.archunit.core.domain.properties.HasName.Predicates.name("deleteByTenantId")))
			.check(production);
	}
}
