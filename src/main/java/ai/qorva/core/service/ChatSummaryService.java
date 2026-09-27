package ai.qorva.core.service;

import ai.qorva.core.config.ResumeChatProperties;
import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dao.repository.ChatMessagesRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dto.common.ChatSummary;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.service.orchestrators.ChatSummarizerAgent;
import ai.qorva.core.service.orchestrators.ConversationWindowBuilder;
import ai.qorva.core.utils.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps {@link Chat#getSummary()} current. Compaction runs after a turn has been answered,
 * off the request path: the turn that crosses the threshold still sends its full window,
 * the next one benefits. A stale summary is never fatal — the window builder bounds the
 * prompt on its own — so any failure here is logged and retried on a later turn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSummaryService {

	private final ChatsRepository chatsRepository;
	private final ChatMessagesRepository chatMessagesRepository;
	private final ChatSummarizerAgent summarizerAgent;
	private final ResumeChatProperties properties;

	/** Messages after the summary cut-off, oldest first, SYSTEM rows excluded. */
	public List<ChatMessage> loadUnsummarised(Chat chat) {
		Instant cutoff = Optional.ofNullable(chat.getSummary()).map(ChatSummary::getUpToMessageCreatedAt).orElse(null);
		Iterable<ChatMessage> rows = cutoff == null
			? chatMessagesRepository.streamForContext(chat.getTenantId(), chat.getId(), ChatUserRole.SYSTEM.name())
			: chatMessagesRepository.streamForContextAfter(chat.getTenantId(), chat.getId(), ChatUserRole.SYSTEM.name(), cutoff);
		List<ChatMessage> out = new java.util.ArrayList<>();
		rows.forEach(out::add);
		return out;
	}

	/** True when the un-summarised tail is large enough to be worth compacting. */
	public boolean shouldCompact(List<ChatMessage> unsummarised) {
		return unsummarised.size() > properties.getKeepRecentMessages()
			&& ConversationWindowBuilder.estimateTokens(unsummarised) > properties.getSummaryTriggerTokens();
	}

	@Async
	public void compactAsync(String tenantId, String chatId) {
		try {
			compact(tenantId, chatId);
		} catch (Exception e) {
			log.warn("Resume chat {}: summary compaction failed, will retry on a later turn", chatId, e);
		}
	}

	/**
	 * Folds everything but the newest {@code keepRecentMessages} un-summarised messages into
	 * the summary. Re-reads the chat first so two quick turns cannot both compact the same
	 * slice: the second one sees the advanced cut-off and does nothing.
	 */
	public void compact(String tenantId, String chatId) {
		Chat chat = chatsRepository.findByIdInTenant(chatId, tenantId).orElse(null);
		if (chat == null) {
			return;
		}
		List<ChatMessage> unsummarised = loadUnsummarised(chat);
		if (!shouldCompact(unsummarised)) {
			return;
		}
		List<ChatMessage> slice = unsummarised.subList(0, unsummarised.size() - properties.getKeepRecentMessages());
		ChatMessage last = slice.get(slice.size() - 1);
		String previous = Optional.ofNullable(chat.getSummary()).map(ChatSummary::getText).orElse(null);
		int previousCount = Optional.ofNullable(chat.getSummary()).map(ChatSummary::getMessageCount).orElse(0);

		String text = summarizerAgent.summarize(previous, slice);

		// Re-read before writing: another turn may have compacted meanwhile.
		Chat fresh = chatsRepository.findByIdInTenant(chatId, tenantId).orElse(null);
		if (fresh == null) {
			return;
		}
		Instant freshCutoff = Optional.ofNullable(fresh.getSummary()).map(ChatSummary::getUpToMessageCreatedAt).orElse(null);
		if (freshCutoff != null && !freshCutoff.isBefore(last.getCreatedAt())) {
			log.debug("Resume chat {}: summary already advanced past {}, dropping this compaction", chatId, last.getId());
			return;
		}
		fresh.setSummary(ChatSummary.builder()
			.text(text)
			.upToMessageId(last.getId())
			.upToMessageCreatedAt(last.getCreatedAt())
			.messageCount(previousCount + slice.size())
			.tokens(TokenEstimator.estimate(text))
			.model(properties.getSummaryModel())
			.updatedAt(Instant.now())
			.build());
		chatsRepository.save(fresh);
		log.info("resume-chat compaction chatId={} folded={} coveredMessages={} summaryTokens={}",
			chatId, slice.size(), previousCount + slice.size(), TokenEstimator.estimate(text));
	}

	public static String summaryText(Chat chat) {
		return Objects.isNull(chat.getSummary()) ? null : chat.getSummary().getText();
	}
}
