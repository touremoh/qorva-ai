package ai.qorva.core.controller;

import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.UserAuthority;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.QorvaApiAccessManager;
import ai.qorva.core.service.S3StorageService;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/** The inherited CRUD routes are closed unless a controller's CrudPolicy opens them. */
@ExtendWith(MockitoExtension.class)
class CrudPolicyControllerTest {

	private static final String ME = "me@acme.test";
	private static final String USER_ID = "64b000000000000000000001";

	@Mock private UserService userService;
	@Mock private TenantService tenantService;
	@Mock private S3StorageService s3StorageService;
	@Mock private QorvaApiAccessManager accessManager;

	private UserController users;
	private TenantController tenants;

	@BeforeEach
	void setUp() {
		users = new UserController(userService);
		users.setAccessManager(accessManager);
		tenants = new TenantController(tenantService, s3StorageService);
		tenants.setAccessManager(accessManager);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ME, null, List.of()));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void users_genericCreateSearchIdsExists_areClosed() {
		assertNotFound(() -> users.createOne("en", new UserDTO()));
		assertNotFound(() -> users.findOneByData(new UserDTO()));
		assertNotFound(() -> users.findManyByIds(List.of(USER_ID)));
		assertNotFound(() -> users.existsByData(new UserDTO()));
		verifyNoInteractions(userService);
	}

	@Test
	void users_delete_requiresManageUsers() {
		when(accessManager.hasPermission(any(), eq("MANAGE_USERS"))).thenReturn(false);

		assertThatThrownBy(() -> users.deleteOneById(USER_ID)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(userService);
	}

	@Test
	void users_profilePatch_onSelf_writesOnlyTheNames() throws QorvaException {
		when(userService.findOneById(USER_ID)).thenReturn(userWithEmail(ME));
		var written = ArgumentCaptor.forClass(UserDTO.class);
		when(userService.updateOne(eq(USER_ID), written.capture())).thenReturn(new UserDTO());

		var payload = userWithEmail("attacker@evil.test");
		payload.setFirstName("Ada");
		payload.setLastName("Lovelace");
		payload.setAuthorities(List.of(new UserAuthority()));
		payload.setEncryptedPassword("$forged");
		payload.setPasswordCredentialVersion(99);
		payload.setTenantId("other-tenant");
		payload.setUserAccountStatus("ACTIVE");

		users.patchOne("en", USER_ID, payload);

		var dto = written.getValue();
		assertThat(dto.getFirstName()).isEqualTo("Ada");
		assertThat(dto.getLastName()).isEqualTo("Lovelace");
		assertThat(dto.getAuthorities()).isNull();
		assertThat(dto.getEncryptedPassword()).isNull();
		assertThat(dto.getPasswordCredentialVersion()).isNull();
		assertThat(dto.getEmail()).isNull();
		assertThat(dto.getTenantId()).isNull();
		assertThat(dto.getUserAccountStatus()).isNull();
	}

	@Test
	void users_profilePatch_onSomeoneElse_requiresManageUsers() throws QorvaException {
		when(userService.findOneById(USER_ID)).thenReturn(userWithEmail("colleague@acme.test"));
		when(accessManager.hasPermission(any(), eq("MANAGE_USERS"))).thenReturn(false);

		assertThatThrownBy(() -> users.updateOne("en", USER_ID, new UserDTO())).isInstanceOf(AccessDeniedException.class);
		verify(userService, never()).updateOne(any(), any());
	}

	@Test
	void users_profilePatch_onSomeoneElse_allowedForManagers() throws QorvaException {
		when(userService.findOneById(USER_ID)).thenReturn(userWithEmail("colleague@acme.test"));
		when(accessManager.hasPermission(any(), eq("MANAGE_USERS"))).thenReturn(true);
		when(userService.updateOne(eq(USER_ID), any())).thenReturn(new UserDTO());

		assertThat(users.patchOne("en", USER_ID, new UserDTO()).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void tenants_onlyGetOneIsOpen() throws QorvaException {
		when(tenantService.findOneById("t1")).thenReturn(new TenantDTO());

		assertThat(tenants.findOneById("t1").getStatusCode()).isEqualTo(HttpStatus.OK);
		assertNotFound(() -> tenants.findAll(Map.of()));
		assertNotFound(() -> tenants.createOne("en", new TenantDTO()));
		assertNotFound(() -> tenants.updateOne("en", "t1", new TenantDTO()));
		assertNotFound(() -> tenants.patchOne("en", "t1", new TenantDTO()));
		assertNotFound(() -> tenants.deleteOneById("t1"));
		assertNotFound(() -> tenants.findManyByIds(List.of("t1")));
	}

	@Test
	void stripeController_doesNotInheritTheGenericCrud() {
		assertThat(AbstractQorvaController.class.isAssignableFrom(StripeController.class)).isFalse();
	}

	private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call)
			.isInstanceOfSatisfying(QorvaException.class,
				e -> assertThat(e.getHttpStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value()));
	}

	private static UserDTO userWithEmail(String email) {
		var user = new UserDTO();
		user.setEmail(email);
		return user;
	}
}
