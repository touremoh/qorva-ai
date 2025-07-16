package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.ProductPrice;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ProductsReferences")
public class ProductReference implements QorvaEntity {

    @Id
    private String id;

    private String productName;
    private String stripeProductId;

    private ProductPrice price;

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
        return this.id;
    }
}
