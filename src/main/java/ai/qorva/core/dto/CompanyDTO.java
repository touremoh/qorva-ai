package ai.qorva.core.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDTO extends AbstractQorvaDTO {
    private String name;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedAt;
}
