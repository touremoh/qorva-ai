package ai.qorva.core.dto;

import ai.qorva.core.dto.common.CompanyInfo;
import ai.qorva.core.dto.common.SubscriptionInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO extends AbstractQorvaDTO {

    private String id;
    private String firstName;
    private String lastName;
    private String email;

    @JsonProperty(access = Access.WRITE_ONLY)
    private String rawPassword;

    @JsonProperty(access = Access.WRITE_ONLY)
    private String encryptedPassword;

    private String userAccountStatus;

    private CompanyInfo companyInfo;
    private SubscriptionInfo subscriptionInfo;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdBy;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedBy;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedAt;

    public String getTenantId() {
        return this.getCompanyInfo().tenantId();
    }
}
