package ai.qorva.core.service;

import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.PendingEmailNotificationDTO;
import ai.qorva.core.enums.EmailNotificationType;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailNotificationDispatcher {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final TenantService tenantService;
    private final ObjectProvider<SubscriptionWelcomeNotificationService> subscriptionWelcomeNotifier;
    private final ObjectProvider<AccountCreationNotificationService> accountCreationNotifier;
    private final ObjectProvider<AgentApprovalDigestNotificationService> agentDigestNotifier;
    private final SetPasswordService setPasswordService;

    @Autowired
    public EmailNotificationDispatcher(
        UserRepository userRepository,
        UserMapper userMapper,
        TenantService tenantService,
        ObjectProvider<SubscriptionWelcomeNotificationService> subscriptionWelcomeNotifier,
        ObjectProvider<AccountCreationNotificationService> accountCreationNotifier,
        ObjectProvider<AgentApprovalDigestNotificationService> agentDigestNotifier,
        SetPasswordService setPasswordService
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.tenantService = tenantService;
        this.subscriptionWelcomeNotifier = subscriptionWelcomeNotifier;
        this.accountCreationNotifier = accountCreationNotifier;
        this.agentDigestNotifier = agentDigestNotifier;
        this.setPasswordService = setPasswordService;
    }

    public void dispatch(PendingEmailNotificationDTO notification) throws QorvaException {
        var type = EmailNotificationType.valueOf(notification.getNotificationType());
        String lang = notification.getLanguageCode();

        var user = userRepository.findByIdInTenant(notification.getUserId(), notification.getTenantId())
            .orElseThrow(() -> new QorvaException("User not found for userId=" + notification.getUserId()));
        var userDTO = userMapper.map(user);
        // Links are minted now, not when queued: the queue never holds a credential. Rows queued before that change
        // still carry theirs and are sent as they are.
        var payload = new HashMap<String, String>(notification.getPayload() != null ? notification.getPayload() : Map.of());

        switch (type) {
            case SUBSCRIPTION_WELCOME -> {
                var tenant = tenantService.findOneById(notification.getTenantId());
                var svc = subscriptionWelcomeNotifier.getIfAvailable();
                if (svc == null) throw new QorvaException("SubscriptionWelcomeNotificationService unavailable");
                svc.send(userDTO, tenant.getSubscriptionInfo(), lang);
            }
            case USER_ADDED -> {
                var svc = accountCreationNotifier.getIfAvailable();
                if (svc == null) throw new QorvaException("AccountCreationNotificationService unavailable");
                var tenant = tenantService.findOneById(notification.getTenantId());
                if (tenant != null && Boolean.TRUE.equals(tenant.getSsoRequired())) {
                    payload.put("signInUrl", setPasswordService.signInUrl());
                    payload.put("sso", "true");
                } else {
                    payload.putIfAbsent("setPasswordUrl", setPasswordService.linkFor(user, type));
                }
                svc.sendUserAdded(userDTO, payload, lang);
            }
            case DEMO_WELCOME -> {
                var svc = accountCreationNotifier.getIfAvailable();
                if (svc == null) throw new QorvaException("AccountCreationNotificationService unavailable");
                payload.putIfAbsent("setPasswordUrl", setPasswordService.linkFor(user, type));
                svc.sendDemoWelcome(userDTO, payload, lang);
            }
            case PASSWORD_RESET -> {
                var svc = accountCreationNotifier.getIfAvailable();
                if (svc == null) throw new QorvaException("AccountCreationNotificationService unavailable");
                payload.putIfAbsent("resetPasswordUrl", setPasswordService.linkFor(user, type));
                svc.sendPasswordReset(userDTO, payload, lang);
            }
            case AGENT_APPROVAL_DIGEST -> {
                var svc = agentDigestNotifier.getIfAvailable();
                if (svc == null) throw new QorvaException("AgentApprovalDigestNotificationService unavailable");
                svc.send(notification.getTenantId(), userDTO, lang);
            }
            default -> throw new QorvaException("No handler registered for notification type: " + type);
        }
    }
}
