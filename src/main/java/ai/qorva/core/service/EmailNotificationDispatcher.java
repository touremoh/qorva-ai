package ai.qorva.core.service;

import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.PendingEmailNotificationDTO;
import ai.qorva.core.enums.EmailNotificationType;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
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

    @Autowired
    public EmailNotificationDispatcher(
        UserRepository userRepository,
        UserMapper userMapper,
        TenantService tenantService,
        ObjectProvider<SubscriptionWelcomeNotificationService> subscriptionWelcomeNotifier,
        ObjectProvider<AccountCreationNotificationService> accountCreationNotifier
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.tenantService = tenantService;
        this.subscriptionWelcomeNotifier = subscriptionWelcomeNotifier;
        this.accountCreationNotifier = accountCreationNotifier;
    }

    public void dispatch(PendingEmailNotificationDTO notification) throws QorvaException {
        var type = EmailNotificationType.valueOf(notification.getNotificationType());
        String lang = notification.getLanguageCode();

        var user = userRepository.findById(new ObjectId(notification.getUserId()))
            .orElseThrow(() -> new QorvaException("User not found for userId=" + notification.getUserId()));
        var userDTO = userMapper.map(user);

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
                svc.sendUserAdded(userDTO, notification.getPayload(), lang);
            }
            default -> throw new QorvaException("No handler registered for notification type: " + type);
        }
    }
}
