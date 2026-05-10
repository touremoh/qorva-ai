package ai.qorva.core.controller;

import ai.qorva.core.dto.ChatDTO;
import ai.qorva.core.dto.ChatMessageDTO;
import ai.qorva.core.dto.request.CreateChatRequest;
import ai.qorva.core.dto.request.PostUserMessageRequest;
import ai.qorva.core.enums.ChatStatus;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private String currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping("/allowed")
    public ResponseEntity<Boolean> isChatPartOfSubscriptionPlan() {
        return ResponseEntity.ok(true);
    }

    @GetMapping
    @PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_CHAT')")
    public Page<ChatDTO> listChats(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "25") int size,
                                   @RequestParam(required = false) ChatStatus status) throws QorvaException {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastUpdatedAt"));
        return chatService.listChats(currentTenantId(), currentUsername(), status, pageable);
    }

    @PostMapping
    @PreAuthorize("@accessManager.hasAuthority(authentication, 'START_CHAT')")
    public ResponseEntity<ChatDTO> createChat(@RequestBody @Valid CreateChatRequest req) throws QorvaException {
        req.setTenantId(currentTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createChat(req, currentUsername()));
    }

    @GetMapping("/{chatId}")
    @PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_CHAT')")
    public ChatDTO getChat(@PathVariable String chatId) throws QorvaException {
        return chatService.getChat(currentTenantId(), chatId);
    }

    @PostMapping("/{chatId}/messages")
    @PreAuthorize("@accessManager.hasAuthority(authentication, 'REPLY_MESSAGE')")
    public ChatMessageDTO postUserMessage(@PathVariable String chatId,
                                          @RequestBody @Valid PostUserMessageRequest req) throws QorvaException {
        req.setTenantId(currentTenantId());
        req.setUsername(currentUsername());
        return chatService.postUserMessage(req.getTenantId(), chatId, req);
    }

    @GetMapping("/{chatId}/messages")
    @PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_MESSAGE')")
    public Page<ChatMessageDTO> getMessages(@PathVariable String chatId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) throws QorvaException {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return chatService.getMessages(currentTenantId(), chatId, pageable);
    }

    @DeleteMapping("/{chatId}")
    @PreAuthorize("@accessManager.hasAuthority(authentication, 'DELETE_CHAT')")
    public ResponseEntity<Void> deleteChat(@PathVariable String chatId) throws QorvaException {
        chatService.deleteChat(currentTenantId(), chatId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{chatId}/status")
    @PreAuthorize("@accessManager.hasAuthority(authentication, 'MODIFY_CHAT')")
    public ResponseEntity<ChatDTO> updateStatus(@PathVariable String chatId,
                                                @RequestParam ChatStatus status) throws QorvaException {
        return ResponseEntity.ok(chatService.updateStatus(currentTenantId(), chatId, status, currentUsername()));
    }
}
