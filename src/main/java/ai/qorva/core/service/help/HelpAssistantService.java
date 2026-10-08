package ai.qorva.core.service.help;

import ai.qorva.core.config.HelpProperties;
import ai.qorva.core.dao.entity.HelpConversation;
import ai.qorva.core.dao.repository.HelpConversationRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.HelpData;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.EmailSenderResolver;
import ai.qorva.core.service.ai.AiCallMetrics;
import ai.qorva.core.service.orchestrators.StructuredOutput;
import ai.qorva.core.utils.SupportedLanguages;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Qorva Help: answers questions about using Qorva from the help text only. Safe by construction rather than by
 * instruction — the model gets no tools and no tenant data, so a successful injection can only change the
 * attacker's own answer; the prompt, the server-side history and {@link HelpAnswerGuard} handle the rest.
 */
@Slf4j
@Service
public class HelpAssistantService {

	static final String PROMPT_FILE = "prompts/Help_assistant_system_prompt.md";
	static final String UI_LABELS_FILE = "help/ui-labels.json";
	private static final String SCHEMA = "help_answer";
	private static final String INSTRUCTIONS_END = "## ALLOWED PAGES";

	private final HelpProperties properties;
	private final HelpKnowledgeBase knowledgeBase;
	private final HelpRateLimiter rateLimiter;
	private final HelpConversationRepository conversations;
	private final UserRepository users;
	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;
	private final BeanOutputConverter<HelpData.ModelAnswer> converter = new BeanOutputConverter<>(HelpData.ModelAnswer.class);
	/** Everything but the language: identical for every call, so the provider's prompt cache applies. */
	private final String systemPrompt;
	private final HelpAnswerGuard guard;
	/** Language → "English label → label in that language" lines; none for English. */
	private final Map<String, String> uiLabels;

	public HelpAssistantService(HelpProperties properties, HelpKnowledgeBase knowledgeBase, HelpRateLimiter rateLimiter,
	                            HelpConversationRepository conversations, UserRepository users,
	                            @Qualifier("helpChatClient") ChatClient chatClient, ObjectMapper objectMapper,
	                            EmailSenderResolver senderResolver) throws IOException {
		this.properties = properties;
		this.knowledgeBase = knowledgeBase;
		this.rateLimiter = rateLimiter;
		this.conversations = conversations;
		this.users = users;
		this.chatClient = chatClient;
		this.objectMapper = objectMapper;
		var template = new ClassPathResource(PROMPT_FILE).getContentAsString(StandardCharsets.UTF_8);
		var canary = "QH-" + HexFormat.of().formatHex(new SecureRandom().generateSeed(8));
		var supportEmail = senderResolver.supportEmail();
		this.systemPrompt = template
			.replace("{{allowed_pages}}", HelpLinks.ALLOWED.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(e -> "- `" + e.getKey() + "`: " + e.getValue()).collect(Collectors.joining("\n")))
			.replace("{{canary}}", canary)
			.replace("{{support_email}}", supportEmail != null ? supportEmail : "(use the Contact support button)")
			.replace("{{knowledge}}", knowledgeBase.text());
		int end = template.indexOf(INSTRUCTIONS_END);
		this.guard = new HelpAnswerGuard(end > 0 ? template.substring(0, end) : template, canary, supportEmail);
		this.uiLabels = loadUiLabels(objectMapper);
	}

	static Map<String, String> loadUiLabels(ObjectMapper objectMapper) throws IOException {
		var json = new ClassPathResource(UI_LABELS_FILE).getContentAsString(StandardCharsets.UTF_8);
		var byLanguage = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
		var result = new LinkedHashMap<String, String>();
		byLanguage.forEach((language, labels) -> {
			if (labels instanceof Map<?, ?> map) {
				result.put(language, map.entrySet().stream()
					.map(e -> "- " + e.getKey() + " → " + e.getValue())
					.collect(Collectors.joining("\n")));
			}
		});
		return Map.copyOf(result);
	}

	public HelpData.Availability availability() {
		return new HelpData.Availability(properties.isEnabled());
	}

	public HelpData.Answer ask(String tenantId, String userEmail, String acceptLanguage, HelpData.AskRequest request)
		throws QorvaException {
		requireEnabled();
		var question = sanitize(request != null ? request.message() : null);
		if (question.isEmpty() || question.length() > properties.getMaxMessageChars()) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.HELP_MESSAGE_INVALID, properties.getMaxMessageChars());
		}
		rateLimiter.acquireMessage(tenantId, userEmail, isDemo(userEmail));

		var language = SupportedLanguages.normalize(acceptLanguage);
		var page = HelpLinks.isAllowed(request.page()) ? request.page() : null;
		var conversation = ownConversation(tenantId, userEmail, request.conversationId());

		var raw = callModel(conversation, question, page, language);
		var checked = guard.check(raw, language);
		if (!checked.reasons().isEmpty()) {
			log.info("help_guard tenant={} reasons={}", tenantId, checked.reasons());
		}
		var answer = checked.answer();

		save(conversation, question, answer, page, language);
		return new HelpData.Answer(conversation.getId(), answer.answer(),
			answer.links().stream().map(HelpData.Link::new).toList(), answer.followUps(), answer.offerSupport());
	}

	/** The caller's own conversation, or a new one — an id that isn't theirs never reveals whether it exists. */
	HelpConversation ownConversation(String tenantId, String userEmail, String conversationId) {
		if (conversationId != null && conversationId.matches("[0-9a-fA-F]{24}")) {
			var found = conversations.findByIdInTenant(conversationId, tenantId)
				.filter(c -> userEmail.equals(c.getUserEmail()));
			if (found.isPresent()) return found.get();
		}
		var conversation = new HelpConversation();
		conversation.setTenantId(tenantId);
		conversation.setUserEmail(userEmail);
		conversation.setCreatedAt(Instant.now());
		return conversation;
	}

	private HelpData.ModelAnswer callModel(HelpConversation conversation, String question, String page, String language)
		throws QorvaException {
		var messages = new ArrayList<Message>();
		messages.add(new SystemMessage(systemPrompt.replace("{{language}}", SupportedLanguages.name(language))
			.replace("{{ui_labels}}", uiLabels.getOrDefault(language, ""))));
		var turns = conversation.getTurns();
		for (var turn : turns.subList(Math.max(0, turns.size() - properties.getHistoryTurns()), turns.size())) {
			messages.add(new UserMessage(wrap(turn.getQuestion(), turn.getPage())));
			messages.add(new AssistantMessage(turn.getAnswer()));
		}
		messages.add(new UserMessage(wrap(question, page)));
		try {
			var model = properties.getModel();
			var options = StructuredOutput.options(model, SCHEMA, converter.getJsonSchema(), false,
				StructuredOutput.temperatureFor(model, 0.2));
			options.setMaxCompletionTokens(properties.getMaxOutputTokens());
			var content = chatClient.prompt()
				.advisors(a -> a.param(AiCallMetrics.AGENT, "help"))
				.options(options)
				.messages(messages)
				.call()
				.content();
			return objectMapper.readValue(content, HelpData.ModelAnswer.class);
		} catch (Exception e) {
			log.error("Qorva Help answer failed: {}", e.getMessage());
			throw QorvaErrors.of(QorvaErrorCodes.HELP_UNAVAILABLE, e, HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	private void save(HelpConversation conversation, String question, HelpData.ModelAnswer answer, String page, String language) {
		var now = Instant.now();
		var turns = new ArrayList<>(conversation.getTurns());
		turns.add(new HelpConversation.Turn(question, answer.answer(), new ArrayList<>(answer.links()), page, now));
		int keep = Math.max(1, properties.getStoredTurns());
		conversation.setTurns(new ArrayList<>(turns.subList(Math.max(0, turns.size() - keep), turns.size())));
		conversation.setLanguage(language);
		conversation.setKbVersion(knowledgeBase.version());
		conversation.setUpdatedAt(now);
		conversation.setExpiresAt(now.plus(Duration.ofDays(properties.getConversationTtlDays())));
		conversations.save(conversation);
	}

	void requireEnabled() throws QorvaException {
		if (!properties.isEnabled()) {
			throw QorvaErrors.of(QorvaErrorCodes.HELP_DISABLED, HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	private boolean isDemo(String userEmail) {
		var user = users.findByEmail(userEmail);
		return user != null && UserStatusEnum.DEMO.getValue().equals(user.getUserAccountStatus());
	}

	/** The question as data: tagged, with any tag of ours the user typed removed. */
	static String wrap(String question, String page) {
		return "<user_question>\n" + question + "\n</user_question>" + (page != null ? "\n<current_page>" + page + "</current_page>" : "");
	}

	/** Trims, drops control and invisible format characters (zero-width, bidi overrides) and our own tags. */
	static String sanitize(String text) {
		if (text == null) return "";
		return text.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "")
			.replaceAll("\\p{Cf}", "")
			.replaceAll("(?i)</?\\s*(user_question|current_page)\\s*>", "")
			.strip();
	}
}
