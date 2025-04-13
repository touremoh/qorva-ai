package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.CompanyInfo;
import ai.qorva.core.dto.common.SubscriptionInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Users")
@CompoundIndex(name = "unique_email_company_idx", def = "{'email': 1, 'companyInfo.tenantId': 1}", unique = true)
public class User implements QorvaEntity {

    @Id
    private String id;

    private String firstName;
    private String lastName;

    private String email;
    private String encryptedPassword;
    private String userAccountStatus;

    private CompanyInfo companyInfo;
    private SubscriptionInfo subscriptionInfo;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    public String getTenantId() {
        return this.getCompanyInfo().tenantId();
    }
}
