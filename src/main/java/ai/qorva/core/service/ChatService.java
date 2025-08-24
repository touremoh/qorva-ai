package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dao.repository.ChatMessagesRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
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
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ChatMapper;
import ai.qorva.core.mapper.ChatMessageMapper;
import ai.qorva.core.service.orchestrators.ChatAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.util.Optional.ofNullable;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatsRepository chatsRepository;
    private final ChatMessagesRepository chatMessagesRepository;
    private final ChatMapper chatMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatAgent agent; // see below
    private final UserRepository userRepository;

    @Transactional
    public ChatDTO createChat(CreateChatRequest req, String username) throws QorvaException {
        if (req.getParticipants().stream().noneMatch(p -> "OWNER".equalsIgnoreCase(p.getRole().name()))) {
            throw new QorvaException("At least one OWNER participant is required");
        }

        // find the user id of the actor
        var userId = ofNullable(userRepository.findByEmail(username)).orElseThrow(() -> new QorvaException("Chat actor not found")).getId();

        Chat chat = Chat.builder()
                .tenantId(req.getTenantId())
                .title(req.getTitle())
                .status(ChatStatus.OPEN)
                .context(ChatContext.builder()
                        .cvId(req.getCvId())
                        .jobPostId(req.getJobPostId())
                        .resumeMatchId(req.getResumeMatchId())
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

        // Optional system seed message describing context (helps LLM)
        ChatMessage system = ChatMessage.builder()
                .tenantId(req.getTenantId())
                .chatId(chat.getId())
                .role(ChatUserRole.SYSTEM)
                .content(buildSystemPreamble(chat))
                .createdAt(Instant.now())
                .build();

        chatMessagesRepository.save(system);

        return chatMapper.map(chat);
    }

    public Page<ChatDTO> listChats(String tenantId, String username, Pageable pageable) throws QorvaException {
        var userId = ofNullable(userRepository.findByEmail(username)).orElseThrow(() -> new QorvaException("User not found")).getId();
        return chatsRepository.findByTenantAndParticipant(tenantId, userId, pageable).map(chatMapper::map);
    }

    public ChatDTO getChat(String tenantId, String chatId) throws QorvaException {
        Chat chat = ofNullable(chatsRepository.findOneByTenantAndId(tenantId, chatId))
            .orElseThrow(() -> new QorvaException("Chat not found"));
        return chatMapper.map(chat);
    }

    @Transactional
    public ChatMessageDTO postUserMessage(String tenantId, String chatId, PostUserMessageRequest req) throws QorvaException {
        // Get the userId
        var userId = ofNullable(userRepository.findByEmail(req.getUsername())).orElseThrow(() -> new QorvaException("User not found")).getId();

        // Get the chat
        Chat chat = ofNullable(chatsRepository.findOneByTenantAndId(tenantId, chatId))
                .orElseThrow(() -> new QorvaException("Chat not found"));

        // Save USER message
        ChatMessage userMsg = ChatMessage.builder()
                .tenantId(tenantId)
                .chatId(chatId)
                .role(ChatUserRole.USER)
                .participantId(userId)
                .content(req.getContent())
                .createdAt(Instant.now())
                .build();
        userMsg = chatMessagesRepository.save(userMsg);

        log.debug("User message saved with ID: {}", userMsg.getId());

        // Build a conversation window (trim if long)
        List<ChatMessage> context = buildConversationWindow(tenantId, chatId);

        // Call LLM (gpt-4o-mini) with CV + Job + ResumeMatch context
        ChatMessage assistant = agent.answer(chat, context);

        assistant = chatMessagesRepository.save(assistant);

        // Update chat lastUpdated
        chat.setLastUpdatedBy(req.getUsername());
        chatsRepository.save(chat);

        return chatMessageMapper.map(assistant);
    }

    public Page<ChatMessageDTO> getMessages(String tenantId, String chatId, Pageable pageable) throws QorvaException {
        if (chatsRepository.findOneByTenantAndId(tenantId, chatId) == null) {
            throw new QorvaException("Chat not found");
        }
        return chatMessagesRepository
            .findPageByTenantAndChatIdExcludingSystemMessage(tenantId, chatId, ChatUserRole.SYSTEM.name(), pageable)
            .map(chatMessageMapper::map);
    }

    @Transactional
    public void closeChat(String tenantId, String chatId, String username) throws QorvaException {
        // find the user id of the actor
        var actor = ofNullable(userRepository.findByEmail(username)).orElseThrow(() -> new QorvaException("Chat actor not found")).getId();

        Chat chat = ofNullable(chatsRepository.findOneByTenantAndId(tenantId, chatId))
            .orElseThrow(() -> new QorvaException("Chat not found"));
        chat.setStatus(ChatStatus.CLOSED);
        chat.setLastUpdatedBy(actor);
        chatsRepository.save(chat);
    }

    // ------- Helpers -------

    private String buildSystemPreamble(Chat chat) {

        return """
            You are Qorva AI, an assistant that answers questions about a candidate's CV in relation to a job post and a resume match analysis.
            Use ONLY the provided context and conversation history. If unsure, say so.
            The chat language is the user's language.
            Context IDs: cvId=%s, jobPostId=%s, resumeMatchId=%s.
            """.formatted(
                chat.getContext().getCvId(),
                chat.getContext().getJobPostId(),
                chat.getContext().getResumeMatchId()
        );
    }

    private List<ChatMessage> buildConversationWindow(String tenantId, String chatId) {
        // Simple windowing: stream all; you can trim by token budget later
        List<ChatMessage> all = new ArrayList<>();
        chatMessagesRepository.streamForContext(tenantId, chatId).forEach(all::add);

        // Optionally trim to last N messages or token estimate
        final int MAX_MSG = 100;
        if (all.size() > MAX_MSG) {
            return all.subList(all.size() - MAX_MSG, all.size());
        }
        return all;
    }
}
