package ai.qorva.core.service;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;

public interface QorvaNotificationService {
	void send(UserDTO user, String languageCode) throws QorvaException;
}
