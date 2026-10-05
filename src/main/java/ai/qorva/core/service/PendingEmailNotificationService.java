package ai.qorva.core.service;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PendingEmailNotificationService extends AbstractQorvaService<PendingEmailNotificationDTO, PendingEmailNotification> {

    static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int MAX_ERROR_LENGTH = 500;

    private final MongoTemplate mongoTemplate;

    @Autowired
    public PendingEmailNotificationService(
        PendingEmailNotificationRepository repository,
        PendingEmailNotificationMapper mapper,
        PendingEmailNotificationQueryBuilder queryBuilder,
        MongoTemplate mongoTemplate
    ) {
        super(repository, mapper, queryBuilder);
        this.mongoTemplate = mongoTemplate;
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

    /** True when a notification of {@code type} was queued for {@code userId} within the last {@code window}. */
    public boolean existsRecent(String userId, EmailNotificationType type, Duration window) {
        var spec = MongoSpecifications.allOf(
            PendingEmailNotificationSpecifications.userIdEquals(userId),
            PendingEmailNotificationSpecifications.notificationTypeEquals(type.name()),
            PendingEmailNotificationSpecifications.createdAfter(Instant.now().minus(window))
        );
        return repository.exists(spec);
    }

    public void markSent(String id) throws QorvaException {
        var dto = findOneById(id);
        dto.setStatus(PendingEmailStatus.SENT.name());
        dto.setProcessedAt(Instant.now());
        updateOne(id, dto);
        clearPayload(id);
    }

    /** Done with: nothing a payload held (names today, links or passwords in older rows) stays at rest. */
    private void clearPayload(String id) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id))),
            new Update().unset("payload"), PendingEmailNotification.class);
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
        if (PendingEmailStatus.FAILED.name().equals(dto.getStatus())) {
            clearPayload(id);
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_ERROR_LENGTH ? s.substring(0, MAX_ERROR_LENGTH) : s;
    }
}
