package ai.qorva.core.service;

import ai.qorva.core.config.ResumeChatProperties;
import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dao.repository.ChatMessagesRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dto.common.ChatSummary;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.service.orchestrators.ChatSummarizerAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSummaryServiceTest {

	private static final String TENANT = "t";
	private static final String CHAT_ID = "c";

	@Mock private ChatsRepository chatsRepository;
	@Mock private ChatMessagesRepository chatMessagesRepository;
	@Mock private ChatSummarizerAgent summarizerAgent;

	private final ResumeChatProperties properties = new ResumeChatProperties();
	private ChatSummaryService service;

	@BeforeEach
	void setUp() {
		properties.setKeepRecentMessages(2);
		properties.setSummaryTriggerTokens(500); // 400-char messages ≈ 100 tokens each
		service = new ChatSummaryService(chatsRepository, chatMessagesRepository, summarizerAgent, properties);
	}

	private static ChatMessage msg(int i) {
		return ChatMessage.builder().id("m" + i).role(i % 2 == 0 ? ChatUserRole.USER : ChatUserRole.ASSISTANT)
			.content("x".repeat(400)).createdAt(Instant.ofEpochSecond(i)).build();
	}

	private static List<ChatMessage> tail(int n) {
		return IntStream.range(0, n).mapToObj(ChatSummaryServiceTest::msg).toList();
	}

	@Test
	void doesNotCompactBelowTheThreshold() {
		assertThat(service.shouldCompact(tail(3))).isFalse();   // 300 tokens < 500
		assertThat(service.shouldCompact(tail(2))).isFalse();   // nothing beyond keepRecent
		assertThat(service.shouldCompact(tail(8))).isTrue();
	}

	@Test
	void foldsEverythingButTheRecentTailIntoTheSummaryAndAdvancesTheCutOff() {
		Chat chat = Chat.builder().id(CHAT_ID).tenantId(TENANT)
			.summary(ChatSummary.builder().text("old").messageCount(4).upToMessageCreatedAt(Instant.ofEpochSecond(-1)).build())
			.build();
		when(chatsRepository.findByIdInTenant(CHAT_ID, TENANT)).thenReturn(Optional.of(chat));
		when(chatMessagesRepository.streamForContextAfter(eq(TENANT), eq(CHAT_ID), eq("SYSTEM"), any())).thenReturn(tail(8));
		when(summarizerAgent.summarize(eq("old"), anyList())).thenReturn("merged");

		service.compact(TENANT, CHAT_ID);

		verify(summarizerAgent).summarize(eq("old"), argThat(slice -> slice.size() == 6 && slice.get(5).getId().equals("m5")));
		verify(chatsRepository).save(chat);
		assertThat(chat.getSummary().getText()).isEqualTo("merged");
		assertThat(chat.getSummary().getUpToMessageId()).isEqualTo("m5");
		assertThat(chat.getSummary().getUpToMessageCreatedAt()).isEqualTo(Instant.ofEpochSecond(5));
		assertThat(chat.getSummary().getMessageCount()).isEqualTo(10);
	}

	@Test
	void skipsTheWriteWhenAnotherTurnAlreadyCompactedPastTheSlice() {
		Chat before = Chat.builder().id(CHAT_ID).tenantId(TENANT).build();
		Chat after = Chat.builder().id(CHAT_ID).tenantId(TENANT)
			.summary(ChatSummary.builder().text("newer").upToMessageCreatedAt(Instant.ofEpochSecond(7)).build()).build();
		when(chatsRepository.findByIdInTenant(CHAT_ID, TENANT)).thenReturn(Optional.of(before), Optional.of(after));
		when(chatMessagesRepository.streamForContext(TENANT, CHAT_ID, "SYSTEM")).thenReturn(tail(8));
		when(summarizerAgent.summarize(any(), anyList())).thenReturn("merged");

		service.compact(TENANT, CHAT_ID);

		verify(chatsRepository, never()).save(any());
	}

	@Test
	void aFailingSummarizerIsSwallowedByTheAsyncEntryPoint() {
		when(chatsRepository.findByIdInTenant(CHAT_ID, TENANT)).thenReturn(Optional.of(Chat.builder().id(CHAT_ID).tenantId(TENANT).build()));
		when(chatMessagesRepository.streamForContext(TENANT, CHAT_ID, "SYSTEM")).thenReturn(tail(8));
		when(summarizerAgent.summarize(any(), anyList())).thenThrow(new IllegalStateException("boom"));

		service.compactAsync(TENANT, CHAT_ID);

		verify(chatsRepository, never()).save(any());
	}
}
