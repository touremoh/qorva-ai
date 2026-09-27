package ai.qorva.core.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantScopeTest {

	@AfterEach
	void reset() {
		TenantContextHolder.clear();
		TenantScope.setFailClosed(true);
	}

	@Test
	void runAs_setsTheTenant_andRestoresThePreviousOne_evenOnFailure() {
		TenantContextHolder.setTenantId("outer");

		TenantScope.runAs("inner", () -> assertThat(TenantScope.current()).isEqualTo("inner"));
		assertThat(TenantScope.current()).isEqualTo("outer");

		assertThatThrownBy(() -> TenantScope.runAs("inner", () -> {
			throw new IllegalStateException("boom");
		})).hasMessage("boom");
		assertThat(TenantScope.current()).isEqualTo("outer");
	}

	@Test
	void runAs_refusesAMissingTenant() {
		assertThatThrownBy(() -> TenantScope.runAs(" ", () -> { })).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TenantScope.runAs(null, () -> { })).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void systemScope_isExplicit_hasNoTenant_andEndsWithTheBlock() {
		TenantContextHolder.setTenantId("t1");

		TenantScope.runAsSystem("catalog sync", () -> {
			assertThat(TenantScope.current()).isNull();
			assertThat(TenantScope.isSystem()).isTrue();
		});

		assertThat(TenantScope.isSystem()).isFalse();
		assertThat(TenantScope.current()).isEqualTo("t1");
		assertThatThrownBy(() -> TenantScope.runAsSystem("", () -> { })).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aTenantInsideASystemBlockIsNotSystem() {
		TenantScope.runAsSystem("webhook", () -> TenantScope.runAs("t1", () -> {
			assertThat(TenantScope.isSystem()).isFalse();
			assertThat(TenantScope.current()).isEqualTo("t1");
		}));
	}

	@Test
	void missing_throwsWhenFailClosed_andOnlyLogsOtherwise() {
		TenantScope.setFailClosed(true);
		assertThatThrownBy(() -> TenantScope.missing("a read")).isInstanceOf(TenantScope.MissingTenantException.class);

		TenantScope.setFailClosed(false);
		assertThatNoException().isThrownBy(() -> TenantScope.missing("a read"));
	}

	@Test
	void propagatingExecutor_runsTasksInTheSubmittersScope_andLeavesWorkerThreadsClean() throws Exception {
		try (var executor = TenantScope.propagating(Executors.newVirtualThreadPerTaskExecutor())) {
			var seen = TenantScope.callAs("t1", () ->
				CompletableFuture.supplyAsync(TenantScope::current, executor).get());
			assertThat(seen).isEqualTo("t1");

			var outside = CompletableFuture.supplyAsync(TenantScope::current, executor).get();
			assertThat(outside).isNull();
		}
	}

	@Test
	void wrappedTasksCarryTheSystemScopeToo() throws Exception {
		try (var executor = TenantScope.propagating(Executors.newSingleThreadExecutor())) {
			var system = TenantScope.callAsSystem("fan-out", () -> executor.submit(TenantScope::isSystem).get());
			assertThat(system).isTrue();
			assertThat(executor.submit(TenantScope::isSystem).get()).isFalse();
		}
	}
}
