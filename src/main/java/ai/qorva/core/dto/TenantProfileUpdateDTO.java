package ai.qorva.core.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TenantProfileUpdateDTO {
    private String tenantName;
    private String companyAddress;
    private String phoneNumber;
    private String contactEmail;
    private String websiteUrl;
}
