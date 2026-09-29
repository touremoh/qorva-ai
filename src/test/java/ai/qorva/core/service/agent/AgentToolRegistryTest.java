package ai.qorva.core.service.agent;

import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.service.QorvaApiAccessManager;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolRegistryTest {

	private static AgentTool tool(String name, Set<UserActionsEnum> actions, boolean available) {
		return new AgentTool() {
			public String name() { return name; }
			public String description() { return name; }
			public String inputSchema() { return "{}"; }
			public AgentRiskTier tier() { return AgentRiskTier.READ; }
			public Set<UserActionsEnum> requiredActions() { return actions; }
			public boolean available(AgentToolContext ctx) { return available; }
			public AgentToolResult execute(JsonNode args, AgentToolContext ctx) { return null; }
		};
	}

	private static AgentToolContext as(String... authorities) {
		var auth = new UsernamePasswordAuthenticationToken("u@a.test", null,
			List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
		return new AgentToolContext("t", "u@a.test", "en", auth);
	}

	private final AgentToolRegistry registry = new AgentToolRegistry(List.of(
		tool("search_cvs", Set.of(UserActionsEnum.VIEW_CV), true),
		tool("list_reports", Set.of(UserActionsEnum.VIEW_REPORT), true),
		tool("send_outreach_email", Set.of(UserActionsEnum.CONTACT_CANDIDATE), false)
	), new QorvaApiAccessManager(null, null));

	@Test
	void offersOnlyTheToolsTheUserHoldsTheRightsFor() {
		assertThat(registry.allowedFor(as("VIEW_CV:ALLOWED")))
			.extracting(AgentTool::name).containsExactly("search_cvs");
		assertThat(registry.allowedFor(as("VIEW_CV:ALLOWED", "VIEW_REPORT:ALLOWED")))
			.extracting(AgentTool::name).containsExactly("list_reports", "search_cvs");
	}

	@Test
	void anUnavailableToolIsHiddenEvenWithTheRight() {
		assertThat(registry.allowedFor(as("CONTACT_CANDIDATE:ALLOWED"))).isEmpty();
		assertThat(registry.allowed("send_outreach_email", as("CONTACT_CANDIDATE:ALLOWED"))).isEmpty();
	}

	@Test
	void executionReChecksTheRight() {
		assertThat(registry.allowed("list_reports", as("VIEW_CV:ALLOWED"))).isEmpty();
		assertThat(registry.allowed("list_reports", as("VIEW_REPORT:ALLOWED"))).isPresent();
		assertThat(registry.allowed("delete_everything", as("VIEW_CV:ALLOWED"))).isEmpty();
	}
}
