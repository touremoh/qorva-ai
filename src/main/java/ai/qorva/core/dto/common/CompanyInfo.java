package ai.qorva.core.dto.common;


import java.time.Instant;

public record CompanyInfo(String name, String tenantId, Instant createdAt, Instant lastUpdatedAt) {
}