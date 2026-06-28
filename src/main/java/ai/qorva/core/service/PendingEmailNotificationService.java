package ai.qorva.core.service;

import ai.qorva.core.dao.entity.PendingEmailNotification;
import ai.qorva.core.dao.querybuilder.PendingEmailNotificationQueryBuilder;
import ai.qorva.core.dao.repository.PendingEmailNotificationRepository;
import ai.qorva.core.dao.specifications.MongoSpecifications;
import ai.qorva.core.dao.specifications.PendingEmailNotificationSpecifications;
import ai.qorva.core.dto.PendingEmailNotificationDTO;
import ai.qorva.core.enums.EmailNotificationType;
import ai.qorva.core.enums.PendingEmailStatus;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.PendingEmailNotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PendingEmailNotificationService extends AbstractQorvaService<PendingEmailNotificationDTO, PendingEmailNotification> {

    static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int MAX_ERROR_LENGTH = 500;

    @Autowired
    public PendingEmailNotificationService(
        PendingEmailNotificationRepository repository,
        PendingEmailNotificationMapper mapper,
        PendingEmailNotificationQueryBuilder queryBuilder
    ) {
        super(repository, mapper, queryBuilder);
    }

    @Override
    protected void preProcessCreateOne(PendingEmailNotificationDTO dto) throws QorvaException {
        super.preProcessCreateOne(dto);
        dto.setStatus(PendingEmailStatus.PENDING.name());
        dto.setAttempts(0);
        dto.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        if (dto.getLanguageCode() == null) {
            dto.setLanguageCode("en");
        }
    }

    public void createPending(String tenantId, String userId, EmailNotificationType type, String languageCode) {
        createPending(tenantId, userId, type, languageCode, null);
    }

    public void createPending(String tenantId, String userId, EmailNotificationType type, String languageCode,
                              Map<String, String> payload) {
        try {
            var dto = new PendingEmailNotificationDTO();
            dto.setTenantId(tenantId);
            dto.setUserId(userId);
            dto.setNotificationType(type.name());
            dto.setLanguageCode(languageCode);
            dto.setPayload(payload);
            createOne(dto);
            log.info("Pending email queued: tenantId={} userId={} type={}", tenantId, userId, type);
        } catch (QorvaException e) {
            log.error("Failed to queue pending email: tenantId={} userId={} type={}", tenantId, userId, type, e);
        }
    }

    public List<PendingEmailNotificationDTO> findPending() {
        var spec = MongoSpecifications.allOf(
            PendingEmailNotificationSpecifications.statusEquals(PendingEmailStatus.PENDING.name()),
            PendingEmailNotificationSpecifications.attemptsLessThan(DEFAULT_MAX_ATTEMPTS)
        );
        return repository.findAll(spec).stream().map(mapper::map).toList();
    }

    public void markSent(String id) throws QorvaException {
        var dto = findOneById(id);
        dto.setStatus(PendingEmailStatus.SENT.name());
        dto.setProcessedAt(Instant.now());
        updateOne(id, dto);
    }

    public void markFailed(String id, String errorMessage) throws QorvaException {
        var dto = findOneById(id);
        dto.setAttempts(dto.getAttempts() + 1);
        dto.setLastError(truncate(errorMessage));
        if (dto.getAttempts() >= dto.getMaxAttempts()) {
            dto.setStatus(PendingEmailStatus.FAILED.name());
            dto.setProcessedAt(Instant.now());
            log.warn("Notification permanently failed after {} attempt(s): id={} type={}",
                dto.getAttempts(), id, dto.getNotificationType());
        }
        updateOne(id, dto);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_ERROR_LENGTH ? s.substring(0, MAX_ERROR_LENGTH) : s;
    }
}
