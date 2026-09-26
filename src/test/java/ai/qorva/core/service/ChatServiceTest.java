package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.ChatMessagesRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.ChatMessageDTO;
import ai.qorva.core.dto.common.ChatSummary;
import ai.qorva.core.dto.request.PostUserMessageRequest;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ChatMapper;
import ai.qorva.core.mapper.ChatMessageMapper;
import ai.qorva.core.service.orchestrators.ChatAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

	private static final String TENANT = "tenant-1";
	private static final String CHAT_ID = "chat-1";

	@Mock private ChatsRepository chatsRepository;
	@Mock private ChatMessagesRepository chatMessagesRepository;
	@Mock private ChatMapper chatMapper;
	@Mock private ChatMessageMapper chatMessageMapper;
	@Mock private ChatAgent agent;
	@Mock private ChatSummaryService chatSummaryService;
	@Mock private UserRepository userRepository;
	@Mock private TransactionTemplate transactionTemplate;

	@Mock private ai.qorva.core.dao.repository.CVRepository cvRepository;
	@Mock private ai.qorva.core.dao.repository.JobPostRepository jobPostRepository;
	@Mock private ai.qorva.core.dao.repository.MatchingReportRepository matchingReportRepository;

	private ChatService service;
	private Chat chat;

	@BeforeEach
	void setUp() {
		service = new ChatService(chatsRepository, chatMessagesRepository, chatMapper, chatMessageMapper,
			agent, chatSummaryService, userRepository, transactionTemplate, cvRepository, jobPostRepository, matchingReportRepository);
		chat = Chat.builder().id(CHAT_ID).tenantId(TENANT).build();

		var user = new User();
		user.setId("user-1");
		when(userRepository.findByEmail("me@qorva.ai")).thenReturn(user);
		when(chatsRepository.findByIdInTenant(CHAT_ID, TENANT)).thenReturn(Optional.of(chat));
		when(chatMessagesRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
			ChatMessage m = inv.getArgument(0);
			if (m.getId() == null) m.setId(m.getRole() + "-saved");
			return m;
		});
	}

	private static PostUserMessageRequest request() {
		return PostUserMessageRequest.builder().tenantId(TENANT).username("me@qorva.ai").content("Is he a fit?").build();
	}

	private static ChatMessage assistant() {
		return ChatMessage.builder().tenantId(TENANT).chatId(CHAT_ID).role(ChatUserRole.ASSISTANT).content("Yes").createdAt(Instant.now()).build();
	}

	@Test
	@SuppressWarnings("unchecked")
	void answersFromTheSummaryAndTheUnsummarisedTailAndCommitsTheReplyWithTheChatTouch() throws QorvaException {
		chat.setSummary(ChatSummary.builder().text("Earlier: asked about Java").build());
		List<ChatMessage> tail = new ArrayList<>(List.of(ChatMessage.builder().role(ChatUserRole.USER).content("hi").build()));
		when(chatSummaryService.loadUnsummarised(chat)).thenReturn(tail);
		when(agent.answer(eq(chat), eq("Earlier: asked about Java"), eq(tail))).thenReturn(assistant());
		when(transactionTemplate.execute(any())).thenAnswer(inv -> ((TransactionCallback<ChatMessage>) inv.getArgument(0)).doInTransaction(null));
		when(chatSummaryService.shouldCompact(anyList())).thenReturn(false);
		when(chatMessageMapper.map(any(ChatMessage.class))).thenAnswer(inv -> {
			ChatMessage m = inv.getArgument(0);
			return ChatMessageDTO.builder().id(m.getId()).content(m.getContent()).role(m.getRole().name()).build();
		});

		ChatMessageDTO dto = service.postUserMessage(TENANT, CHAT_ID, request());

		assertThat(dto.getRole()).isEqualTo("ASSISTANT");
		assertThat(dto.getContent()).isEqualTo("Yes");
		assertThat(chat.getLastUpdatedBy()).isEqualTo("user-1");
		verify(chatsRepository).save(chat);
		verify(chatSummaryService, never()).compactAsync(anyString(), anyString());
		// the reply was appended to the tail before the compaction check
		assertThat(tail).hasSize(2);
	}

	@Test
	void keepsTheUserMessageWhenTheModelCallFails() throws QorvaException {
		when(chatSummaryService.loadUnsummarised(chat)).thenReturn(new ArrayList<>());
		when(agent.answer(any(), any(), anyList())).thenThrow(new QorvaException(QorvaErrorCodes.AI_REQUEST_FAILED));

		assertThatThrownBy(() -> service.postUserMessage(TENANT, CHAT_ID, request()))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AI_REQUEST_FAILED);

		verify(chatMessagesRepository).save(any(ChatMessage.class)); // the USER row
		verify(transactionTemplate, never()).execute(any());
		verify(chatsRepository, never()).save(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void aRetryOfAnUnansweredQuestionReusesTheStoredUserMessage() throws QorvaException {
		ChatMessage pending = ChatMessage.builder().id("USER-pending").role(ChatUserRole.USER).content("Is he a fit?").build();
		when(chatMessagesRepository.findFirstByTenantIdAndChatIdOrderByCreatedAtDesc(TENANT, CHAT_ID)).thenReturn(pending);
		when(chatSummaryService.loadUnsummarised(chat)).thenReturn(new ArrayList<>());
		when(agent.answer(any(), any(), anyList())).thenReturn(assistant());
		when(transactionTemplate.execute(any())).thenAnswer(inv -> ((TransactionCallback<ChatMessage>) inv.getArgument(0)).doInTransaction(null));
		when(chatMessageMapper.map(any(ChatMessage.class))).thenReturn(ChatMessageDTO.builder().build());

		service.postUserMessage(TENANT, CHAT_ID, request());

		verify(chatMessagesRepository, never()).save(argThat(m -> m.getRole() == ChatUserRole.USER));
	}

	@Test
	@SuppressWarnings("unchecked")
	void schedulesACompactionOnceTheTailIsLargeEnough() throws QorvaException {
		when(chatSummaryService.loadUnsummarised(chat)).thenReturn(new ArrayList<>());
		when(agent.answer(any(), any(), anyList())).thenReturn(assistant());
		when(transactionTemplate.execute(any())).thenAnswer(inv -> ((TransactionCallback<ChatMessage>) inv.getArgument(0)).doInTransaction(null));
		when(chatSummaryService.shouldCompact(anyList())).thenReturn(true);
		when(chatMessageMapper.map(any(ChatMessage.class))).thenReturn(ChatMessageDTO.builder().build());

		service.postUserMessage(TENANT, CHAT_ID, request());

		verify(chatSummaryService).compactAsync(TENANT, CHAT_ID);
	}
}
