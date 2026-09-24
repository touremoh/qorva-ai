package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.UserAuthority;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User implements QorvaEntity {

    @Id
    private String id;

    private String firstName;
    private String lastName;

    @TextIndexed(weight = 5)
    private String email;

    private String encryptedPassword;

    private String userAccountStatus; // Expected: UserStatusEnum values (ACTIVE, INACTIVE, LOCKED, DELETED, TRIAL_PERIOD, PENDING_SUBSCRIPTION, DEMO)
    private String communicationLanguage;

    /**
     * Monotonic counter bumped on every password change. Set-password tokens embed the version
     * at mint time; a token whose version no longer matches is considered already used (single-use).
     */
    private Integer passwordCredentialVersion;

    /** Version to embed in / compare against a set-password token; legacy users have no version yet. */
    public int getPasswordCredentialVersionOrZero() {
        return passwordCredentialVersion != null ? passwordCredentialVersion : 0;
    }

    /**
     * Email MFA opt-in. A {@code Boolean} on purpose: the generic user update merges only null
     * fields from the stored document, so a primitive would reset to false on every profile save.
     * Only {@code MfaService} changes it, after a verified code.
     */
    private Boolean mfaEnabled;
    private Instant mfaEnabledAt;

    /** Legacy users have no flag yet: off. */
    public boolean isMfaEnabledOrFalse() {
        return Boolean.TRUE.equals(mfaEnabled);
    }

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    List<UserAuthority> authorities;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;
}
