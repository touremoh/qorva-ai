package ai.qorva.core.controller;


import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.ChatDTO;
import ai.qorva.core.dto.ChatMessageDTO;
import ai.qorva.core.dto.request.CreateChatRequest;
import ai.qorva.core.dto.request.PostUserMessageRequest;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ChatService;
import ai.qorva.core.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('Professional', 'Enterprise', 'FREE_TRIAL_PERIOD_ACTIVE')")
public class ChatController {

    private final ChatService chatService;
    protected final JwtConfig jwtConfig;

    @GetMapping("/allowed")
    public ResponseEntity<Boolean> isChatPartOfSubscriptionPlan() {
        return ResponseEntity.ok(true);
    }

    @GetMapping
    public Page<ChatDTO> listChats(@RequestHeader("Authorization") String authorizationHeader,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "25") int size) throws QorvaException {
        String tenantId = JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
        String username = JwtUtils.extractUsername(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastUpdatedAt"));
        return chatService.listChats(tenantId, username, pageable);
    }

    @PostMapping
    public ResponseEntity<ChatDTO> createChat(@RequestHeader("Authorization") String authorizationHeader,
                                              @RequestBody @Valid CreateChatRequest req) throws QorvaException {
        req.setTenantId(JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey()));
        String username = JwtUtils.extractUsername(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createChat(req, username));
    }

    @GetMapping("/{chatId}")
    public ChatDTO getChat(@PathVariable String chatId, @RequestHeader("Authorization") String authorizationHeader) throws QorvaException {
        return chatService.getChat(JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey()), chatId);
    }

    @PostMapping("/{chatId}/messages")
    public ChatMessageDTO postUserMessage(@PathVariable String chatId,
                                          @RequestHeader("Authorization") String authorizationHeader,
                                          @RequestBody @Valid PostUserMessageRequest req) throws QorvaException {
        req.setTenantId(JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey()));
        req.setUsername(JwtUtils.extractUsername(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey()));
        return chatService.postUserMessage(req.getTenantId(), chatId, req);
    }

    @GetMapping("/{chatId}/messages")
    public Page<ChatMessageDTO> getMessages(@RequestHeader("Authorization") String authorizationHeader,
                                            @PathVariable String chatId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) throws QorvaException {
        String tenantId = JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return chatService.getMessages(tenantId, chatId, pageable);
    }

    @PostMapping("/{chatId}/close")
    public ResponseEntity<Void> close(@PathVariable String chatId,
                                      @RequestHeader("Authorization") String authorizationHeader) throws QorvaException {
        String tenantId = JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
        String username = JwtUtils.extractUsername(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
        chatService.closeChat(tenantId, chatId, username);
        return ResponseEntity.noContent().build();
    }
}
