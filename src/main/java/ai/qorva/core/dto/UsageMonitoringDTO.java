package ai.qorva.core.dto;

import ai.qorva.core.dto.common.UsageFeatures;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UsageMonitoringDTO extends AbstractQorvaDTO {

    private String id;
    private String tenantId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant currentPeriodStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant currentPeriodEnd;

    private String subscriptionTier;
    private UsageFeatures features;

    /** Static plan cap for bulk CV imports — populated per request, not persisted. */
    private Integer bulkUploadFilesLimit;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastUpdatedAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdBy;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedBy;

    @Override
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
