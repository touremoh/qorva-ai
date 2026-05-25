package ai.qorva.core.dto;

import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.dto.common.StripePrice;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReferenceDTO extends AbstractQorvaDTO {

    private String id;

    private String stripeProductId;
    private String name;
    private String description;
    private boolean active;
    private Map<String, String> metadata;

    private List<StripePrice> prices;
    private ProductFeatures features;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdBy;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedBy;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastUpdatedAt;

    @Override
    public String getTenantId() {
        return null; // global entity — not scoped to any tenant
    }

    @Override
    public void setTenantId(String tenantId) {
        // no-op
    }
}
