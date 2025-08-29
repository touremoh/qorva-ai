package ai.qorva.core.dto;

import ai.qorva.core.dto.common.SubscriptionInfo;
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
public class TenantDTO extends AbstractQorvaDTO {
    private String id;
    private String tenantName;
    private String stripeCustomerId;
    private SubscriptionInfo subscriptionInfo;

    private String createdBy;
    private String lastUpdatedBy;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastUpdatedAt;

    @Override
    public String getTenantId() {
        return this.id;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.id = tenantId;
    }
}
