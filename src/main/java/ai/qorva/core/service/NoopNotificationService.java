package ai.qorva.core.service;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "false")
public class NoopNotificationService extends AbstractEmailService {

	@Autowired
	public NoopNotificationService(OAuth2TokenService oauth2TokenService) {
		super(oauth2TokenService);
	}

	@Override
	public void send(UserDTO user, String languageCode) throws QorvaException {
		log.info("Email for user {} not sent. Notification service is disabled.", user.getEmail());
	}
}
