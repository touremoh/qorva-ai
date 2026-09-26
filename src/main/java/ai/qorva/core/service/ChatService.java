package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dao.repository.ChatMessagesRepository;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dao.repository.OwnedLookup;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.ChatDTO;
import ai.qorva.core.dto.ChatMessageDTO;
import ai.qorva.core.dto.common.ChatContext;
import ai.qorva.core.dto.common.ChatMetadata;
import ai.qorva.core.dto.common.Participant;
import ai.qorva.core.dto.request.CreateChatRequest;
import ai.qorva.core.dto.request.PostUserMessageRequest;
import ai.qorva.core.enums.ChatStatus;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ChatMapper;
import ai.qorva.core.mapper.ChatMessageMapper;
import ai.qorva.core.service.orchestrators.ChatAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static java.util.Optional.ofNullable;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatsRepository chatsRepository;
    private final ChatMessagesRepository chatMessagesRepository;
    private final ChatMapper chatMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatAgent agent;
    private final ChatSummaryService chatSummaryService;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final CVRepository cvRepository;
    private final JobPostRepository jobPostRepository;
    private final MatchingReportRepository matchingReportRepository;

    @Transactional
    public ChatDTO createChat(CreateChatRequest req, String username) throws QorvaException {
        // The chat's context is read on every turn: it may only name the caller's own documents.
        requireInTenant(cvRepository, req.getCvId(), req.getTenantId());
        requireInTenant(jobPostRepository, req.getJobPostId(), req.getTenantId());
        requireInTenant(matchingReportRepository, req.getMatchingReportId(), req.getTenantId());
        if (req.getParticipants().stream().noneMatch(p -> "OWNER".equalsIgnoreCase(p.getRole().name()))) {
            throw new QorvaException(QorvaErrorCodes.CHAT_OWNER_REQUIRED);
        }

        // find the user id of the actor
        var userId = ofNullable(userRepository.findByEmail(username)).orElseThrow(() -> new QorvaException(QorvaErrorCodes.CHAT_ACTOR_NOT_FOUND)).getId();

        Chat chat = Chat.builder()
                .tenantId(req.getTenantId())
                .title(req.getTitle())
                .status(ChatStatus.OPEN)
                .context(ChatContext.builder()
                        .cvId(req.getCvId())
                        .jobPostId(req.getJobPostId())
                        .matchingReportId(req.getMatchingReportId())
                        .build())
                .participants(req.getParticipants().stream()
                        .map(p -> Participant.builder()
                                .userId(userId)
                                .role(Participant.Role.valueOf(p.getRole().name().toUpperCase()))
                                .build())
                        .toList())
                .metadata(ChatMetadata.builder()
                        .language(req.getLanguage())
                        .tags(ofNullable(req.getTags()).orElseGet(List::of))
                        .build())
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .createdBy(userId)
                .lastUpdatedBy(userId)
                .build();

        chat = chatsRepository.save(chat);

        return chatMapper.map(chat);
    }

    public Page<ChatDTO> listChats(String tenantId, String username, ChatStatus status, Pageable pageable) throws QorvaException {
        var userId = ofNullable(userRepository.findByEmail(username)).orElseThrow(() -> new QorvaException(QorvaErrorCodes.USER_NOT_FOUND)).getId();
        if (status != null) {
            return chatsRepository.findByTenantAndParticipantAndStatus(tenantId, userId, status.name(), pageable).map(chatMapper::map);
        }
        return chatsRepository.findByTenantAndParticipant(tenantId, userId, pageable).map(chatMapper::map);
    }

    public ChatDTO getChat(String tenantId, String chatId) throws QorvaException {
        return chatMapper.map(requireChat(tenantId, chatId));
    }

    /**
     * One turn. Deliberately not transactional as a whole: the model call can take tens of
     * seconds and MongoDB aborts transactions after 60 s, which used to fail the save of an
     * answer that had actually arrived. The USER message is written first and survives a
     * failed model call; the reply and the chat touch are committed together afterwards.
     */
    public ChatMessageDTO postUserMessage(String tenantId, String chatId, PostUserMessageRequest req) throws QorvaException {
        var userId = ofNullable(userRepository.findByEmail(req.getUsername())).orElseThrow(() -> new QorvaException(QorvaErrorCodes.USER_NOT_FOUND)).getId();

        Chat chat = requireChat(tenantId, chatId);

        // A retry after a failed model call re-posts the same text: reuse the unanswered row instead of duplicating it.
        ChatMessage newest = chatMessagesRepository.findFirstByTenantIdAndChatIdOrderByCreatedAtDesc(tenantId, chatId);
        ChatMessage userMsg = isUnansweredDuplicate(newest, req.getContent())
                ? newest
                : chatMessagesRepository.save(ChatMessage.builder()
                        .tenantId(tenantId)
                        .chatId(chatId)
                        .role(ChatUserRole.USER)
                        .participantId(userId)
                        .content(req.getContent())
                        .createdAt(Instant.now())
                        .build());
        log.debug("User message saved with ID: {}", userMsg.getId());

        // Summary + bounded window instead of the whole transcript
        List<ChatMessage> unsummarised = chatSummaryService.loadUnsummarised(chat);
        ChatMessage assistant = agent.answer(chat, ChatSummaryService.summaryText(chat), unsummarised);

        ChatMessage saved = transactionTemplate.execute(status -> {
            ChatMessage persisted = chatMessagesRepository.save(assistant);
            chat.setLastUpdatedBy(userId);
            chat.setLastUpdatedAt(Instant.now());
            chatsRepository.save(chat);
            return persisted;
        });

        unsummarised.add(saved);
        if (chatSummaryService.shouldCompact(unsummarised)) {
            chatSummaryService.compactAsync(tenantId, chatId);
        }

        return chatMessageMapper.map(saved);
    }

    /** An optional context id must name a document of the tenant; a foreign and a missing one are the same 404. */
    private static void requireInTenant(OwnedLookup<?> repository, String id, String tenantId) throws QorvaException {
        if (id != null && !id.isBlank() && repository.findByIdInTenant(id, tenantId).isEmpty()) {
            throw new QorvaException(QorvaErrorCodes.HTTP_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
        }
    }

    /** The chat if it belongs to the tenant; a missing and a foreign chat are the same 404. */
    private Chat requireChat(String tenantId, String chatId) throws QorvaException {
        return chatsRepository.findByIdInTenant(chatId, tenantId)
            .orElseThrow(() -> new QorvaException(QorvaErrorCodes.CHAT_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
    }

    private static boolean isUnansweredDuplicate(ChatMessage newest, String content) {
        return newest != null && newest.getRole() == ChatUserRole.USER && Objects.equals(newest.getContent(), content);
    }

    public Page<ChatMessageDTO> getMessages(String tenantId, String chatId, Pageable pageable) throws QorvaException {
        requireChat(tenantId, chatId);
        return chatMessagesRepository
            .findPageByTenantAndChatIdExcludingSystemMessage(tenantId, chatId, ChatUserRole.SYSTEM.name(), pageable)
            .map(chatMessageMapper::map);
    }

    @Transactional
    public void deleteChat(String tenantId, String chatId) throws QorvaException {
        Chat chat = requireChat(tenantId, chatId);
        long deleted = chatMessagesRepository.deleteByTenantIdAndChatId(tenantId, chatId);
        chatsRepository.delete(chat);
        log.debug("Deleted chat {} with {} messages", chatId, deleted);
    }

    @Transactional
    public ChatDTO updateStatus(String tenantId, String chatId, ChatStatus status, String username) throws QorvaException {
        var actor = ofNullable(userRepository.findByEmail(username)).orElseThrow(() -> new QorvaException(QorvaErrorCodes.CHAT_ACTOR_NOT_FOUND)).getId();
        Chat chat = requireChat(tenantId, chatId);
        chat.setStatus(status);
        chat.setLastUpdatedBy(actor);
        chat.setLastUpdatedAt(Instant.now());
        return chatMapper.map(chatsRepository.save(chat));
    }
}
