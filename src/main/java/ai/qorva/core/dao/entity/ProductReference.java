package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.StripePrice;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stripe_product_references")
public class ProductReference implements QorvaEntity {

    @Id
    private String id;

    private String stripeProductId;
    private String name;
    private String description;
    private boolean active;
    private Map<String, String> metadata;

    private List<StripePrice> prices;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;

    @Override
    public String getTenantId() {
        return null; // global entity — not scoped to any tenant
    }
}
