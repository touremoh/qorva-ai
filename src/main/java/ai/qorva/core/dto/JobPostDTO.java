package ai.qorva.core.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostDTO extends AbstractQorvaDTO {
    private String id;
    private String companyId;
    private String title;
    private String description;
    private String createdBy;
    private String lastUpdatedBy;

    @JsonProperty(access = Access.READ_ONLY)
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    private Instant lastUpdatedAt;
}
