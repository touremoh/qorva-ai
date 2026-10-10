package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.SubscriptionInfo;
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
@Document(collection = "tenants")
public class Tenant implements QorvaEntity {

    @Id
    private String id;

    private String tenantName;
    private String organizationId;
    private String recruitmentType;
    private String organizationSize;
    private String companyAddress;
    private String companyLogoUrl;
    private String phoneNumber;
    private String contactEmail;
    private String websiteUrl;
    private String stripeCustomerId;

    private SubscriptionInfo subscriptionInfo;

    /** Users must sign in with Microsoft; password sign-in is refused, except for the account owner (break-glass). */
    private Boolean ssoRequired;

    /** {@link ai.qorva.core.enums.TenantStatusEnum}; absent = ACTIVE. Changed only from the admin console. */
    private String status;
    private String statusReason;
    private Instant statusChangedAt;
    private String statusChangedBy;

    /** Soft delete: set with status DELETED; the data is purged once {@code purgeAfter} has passed. */
    private Instant deletedAt;
    private Instant purgeAfter;

    /** {@link ai.qorva.core.enums.TenantAccountTypeEnum}; absent = customer or demo. */
    private String accountType;

    /** Test accounts and the like: left out of the business statistics. */
    private Boolean internal;

    /** Sign-in and tokens stop at this instant (test accounts); absent = no end. */
    private Instant accessExpiresAt;

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
