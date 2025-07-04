package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostDTO extends AbstractQorvaDTO {
    private String id;
    private String tenantId;
    private String title;
    private String description;
    private String createdBy;
    private String lastUpdatedBy;
    private String status;

    @JsonProperty(access = Access.WRITE_ONLY)
    private float[] embedding;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastUpdatedAt;

    public String toJobTitleAndDescription() {
        return "Job Title: " + getTitle() + "\n Job Description: " + getDescription();
    }
}
