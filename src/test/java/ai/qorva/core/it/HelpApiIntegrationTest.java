package ai.qorva.core.it;

import ai.qorva.core.dao.entity.SupportTicket;
import ai.qorva.core.service.help.SupportTicketEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Qorva Help end to end with the model scripted: the answer goes through the guard, the language comes from
 * Accept-Language, history is read from Mongo only for the caller's own conversation, and support tickets are
 * stored, emailed and limited.
 */
@TestPropertySource(properties = {"qorva.help.enabled=true", "qorva.help.limits.tickets-per-user-per-day=3"})
class HelpApiIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;
	private static final String MODEL_ANSWER = """
		{"answer":"Open **Copilot → Rules** and click **New rule**. More at https://evil.test/x","links":["copilot.rules","https://evil.test"],
		"followUps":["How do I pause a rule?"],"offerSupport":false}""";

	@MockitoBean
	private ChatModel chatModel;
	@MockitoBean
	private SupportTicketEmailService ticketEmails;

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;
	@Autowired
	private ObjectMapper objectMapper;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;
	private String owner;
	private final List<List<Message>> prompts = new ArrayList<>();

	@BeforeEach
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		mongo.remove(new Query(), "help_conversations");
		mongo.remove(new Query(), "support_tickets");
		prompts.clear();
		when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
			prompts.add(List.copyOf(inv.<Prompt>getArgument(0).getInstructions()));
			return new ChatResponse(List.of(new Generation(new AssistantMessage(MODEL_ANSWER))));
		});
	}

	private JsonNode ask(String token, String language, Map<String, Object> body, int expectedStatus) throws Exception {
		var response = mvc.perform(post("/help/messages").header("Authorization", token).header("Accept-Language", language)
				.contentType(JSON).content(objectMapper.writeValueAsString(body)))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
		return objectMapper.readTree(response.getContentAsString());
	}

	private static String system(List<Message> prompt) {
		return prompt.getFirst().getText();
	}

	@Test
	void availabilityAndAuthentication() throws Exception {
		var availability = mvc.perform(get("/help/availability").header("Authorization", owner)).andReturn().getResponse();
		assertThat(objectMapper.readTree(availability.getContentAsString()).path("enabled").asBoolean()).isTrue();
		var anonymous = mvc.perform(post("/help/messages").contentType(JSON).content("{\"message\":\"hi\"}")).andReturn().getResponse();
		assertThat(anonymous.getStatus()).isIn(401, 403);
	}

	@Test
	void anAnswerIsGuardedAndWrittenInTheUiLanguage() throws Exception {
		var answer = ask(owner, "fr-FR", Map.of("message", "Comment créer une règle ?", "page", "copilot.rules"), 200);

		assertThat(answer.path("answer").asText()).contains("**New rule**").doesNotContain("evil.test", "https://");
		assertThat(answer.path("links").findValuesAsText("key")).containsExactly("copilot.rules");
		assertThat(answer.path("followUps").get(0).asText()).isEqualTo("How do I pause a rule?");
		assertThat(answer.path("conversationId").asText()).hasSize(24);

		var prompt = prompts.getFirst();
		assertThat(prompt.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(system(prompt)).contains("Write `answer` and `followUps` in French", "# Supported ATS", "# Plan limits",
				"- Resume Library → CVthèque")
			// Every placeholder of the prompt is filled (the help text itself may quote a rule's {{candidates}}).
			.doesNotContain("{{language}}", "{{ui_labels}}", "{{knowledge}}", "{{canary}}", "{{allowed_pages}}", "{{support_email}}");
		assertThat(prompt.getLast().getText())
			.isEqualTo("<user_question>\nComment créer une règle ?\n</user_question>\n<current_page>copilot.rules</current_page>");
	}

	@Test
	void followUpsReplayTheStoredHistoryOnlyForItsOwner() throws Exception {
		var first = ask(owner, "en", Map.of("message", "How do I connect Recruitee?"), 200);
		var conversationId = first.path("conversationId").asText();

		var second = ask(owner, "en", Map.of("message", "And Lever?", "conversationId", conversationId), 200);
		assertThat(second.path("conversationId").asText()).isEqualTo(conversationId);
		var followUp = prompts.get(1);
		assertThat(followUp).hasSize(4);
		assertThat(followUp.get(1).getText()).contains("How do I connect Recruitee?");
		// The replayed answer is the guarded one stored server-side, never something the client sent.
		assertThat(followUp.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
		assertThat(followUp.get(2).getText()).doesNotContain("evil.test");

		// A colleague in the same company and a user of another company both get a fresh conversation.
		var viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
		var colleague = ask(viewer, "en", Map.of("message", "Hi", "conversationId", conversationId), 200);
		assertThat(colleague.path("conversationId").asText()).isNotEqualTo(conversationId);
		assertThat(prompts.get(2)).hasSize(2);

		var other = fixture.bearer(b.ownerEmail(), b.tenantId());
		var stranger = ask(other, "en", Map.of("message", "Hi", "conversationId", conversationId), 200);
		assertThat(stranger.path("conversationId").asText()).isNotEqualTo(conversationId);
		assertThat(prompts.get(3)).hasSize(2);

		var stored = mongo.findById(new org.bson.types.ObjectId(conversationId), org.bson.Document.class, "help_conversations");
		assertThat(stored).isNotNull();
		assertThat(stored.getList("turns", org.bson.Document.class)).hasSize(2);
		assertThat(stored.get("expiresAt")).isNotNull();
	}

	@Test
	void anInvalidQuestionIsRefusedWithoutCallingTheModel() throws Exception {
		ask(owner, "en", Map.of("message", "x".repeat(1001)), 400);
		ask(owner, "en", Map.of("message", "​ \u0007 "), 400);
		assertThat(prompts).isEmpty();
	}

	@Test
	void aModelFailureIsA503() throws Exception {
		when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
		var error = ask(owner, "de", Map.of("message", "Hallo"), 503);
		assertThat(error.path("errorCode").asText()).isEqualTo("error.help.unavailable");
	}

	@Test
	void aTicketIsStoredWithTheOwnConversationAndEmailed() throws Exception {
		var conversationId = ask(owner, "en", Map.of("message", "Sync fails with Auth error"), 200).path("conversationId").asText();

		var response = mvc.perform(post("/help/tickets").header("Authorization", owner).header("Accept-Language", "en")
				.contentType(JSON).content(objectMapper.writeValueAsString(Map.of("conversationId", conversationId,
					"subject", "Sync\r\nBcc: x@evil.test", "description", "Recruitee says Auth error.", "includeConversation", true,
					"page", "settings.integrations"))))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		var reference = objectMapper.readTree(response.getContentAsString()).path("reference").asText();
		assertThat(reference).matches("QH-[A-Z2-9]{6}");

		var ticket = mongo.findOne(Query.query(Criteria.where("reference").is(reference)), SupportTicket.class);
		assertThat(ticket).isNotNull();
		assertThat(ticket.getTenantId()).isEqualTo(a.tenantId());
		assertThat(ticket.getUserEmail()).isEqualTo(a.ownerEmail());
		assertThat(ticket.getSubject()).isEqualTo("Sync Bcc: x@evil.test").doesNotContain("\n", "\r");
		assertThat(ticket.getTranscript()).hasSize(1);
		assertThat(ticket.getPage()).isEqualTo("settings.integrations");
		assertThat(ticket.getEmailStatus()).isEqualTo(SupportTicket.EMAIL_SENT);
		var sent = ArgumentCaptor.forClass(SupportTicket.class);
		verify(ticketEmails, times(1)).send(sent.capture());
		assertThat(sent.getValue().getReference()).isEqualTo(reference);
	}

	@Test
	void aTicketNeverCarriesSomeoneElsesConversation() throws Exception {
		var conversationId = ask(owner, "en", Map.of("message", "Private question"), 200).path("conversationId").asText();
		var other = fixture.bearer(b.ownerEmail(), b.tenantId());
		var response = mvc.perform(post("/help/tickets").header("Authorization", other).contentType(JSON)
				.content(objectMapper.writeValueAsString(Map.of("conversationId", conversationId, "subject", "Hi",
					"description", "Hello", "includeConversation", true))))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(201);
		var ticket = mongo.findOne(Query.query(Criteria.where("userEmail").is(b.ownerEmail())), SupportTicket.class);
		assertThat(ticket.getTranscript()).isEmpty();
		assertThat(ticket.getTenantId()).isEqualTo(b.tenantId());
	}

	@Test
	void ticketsAreValidatedAndLimitedPerUser() throws Exception {
		var viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
		var invalid = mvc.perform(post("/help/tickets").header("Authorization", viewer).contentType(JSON)
			.content("{\"subject\":\"\",\"description\":\"x\"}")).andReturn().getResponse();
		assertThat(invalid.getStatus()).isEqualTo(400);
		var body = "{\"subject\":\"Question\",\"description\":\"Please help\",\"includeConversation\":false}";
		for (int i = 0; i < 3; i++) {
			assertThat(mvc.perform(post("/help/tickets").header("Authorization", viewer).contentType(JSON).content(body))
				.andReturn().getResponse().getStatus()).isEqualTo(201);
		}
		var limited = mvc.perform(post("/help/tickets").header("Authorization", viewer).contentType(JSON).content(body))
			.andReturn().getResponse();
		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(objectMapper.readTree(limited.getContentAsString()).path("errorCode").asText()).isEqualTo("error.help.ticket_rate_limited");
	}
}
