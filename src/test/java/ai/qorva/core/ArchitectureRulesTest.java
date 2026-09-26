package ai.qorva.core;

import ai.qorva.core.controller.AbstractQorvaController;
import ai.qorva.core.security.TenantContextHolder;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural rules the refactor must keep true. Each one holds on today's code; later phases add the
 * stricter ones from the refactor guide (repository access only through the tenant-scoped base,
 * TenantScope instead of TenantContextHolder) as they become true.
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
	 * Only the JWT filter (requests) and TenantScope (everything else, with restore) set the tenant;
	 * nothing may switch it ad hoc.
	 */
	@Test
	void onlyTheJwtFilterAndTenantScopeSetTheTenantContext() {
		noClasses().that().doNotHaveSimpleName("JwtRequestFilter").and().doNotHaveSimpleName("TenantScope")
			.should().callMethod(TenantContextHolder.class, "setTenantId", String.class)
			.check(production);
	}

	/** Persistence never reaches up into the web or service layers. */
	@Test
	void daoDoesNotDependOnServicesOrControllers() {
		noClasses().that().resideInAPackage("ai.qorva.core.dao..")
			.should().dependOnClassesThat().resideInAnyPackage("ai.qorva.core.service..", "ai.qorva.core.controller..")
			.check(production);
	}

	/**
	 * Controllers go through services, never straight to a repository. One known exception: the
	 * public ATS webhook looks its connection up by id before any tenant is known — refactor phase 5
	 * moves that into a service that runs under the connection's tenant, then remove the exemption.
	 */
	@Test
	void controllersDoNotUseRepositories() {
		noClasses().that().resideInAPackage("ai.qorva.core.controller..")
			.and().doNotHaveSimpleName("AtsPublicController")
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
