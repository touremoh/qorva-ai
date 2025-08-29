package ai.qorva.core.dto.request;

import ai.qorva.core.dto.common.Participant;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateChatRequest {
    private String tenantId;
    private String title;
    private String cvId;
    private String jobPostId;
    private String resumeMatchId;

    @NotEmpty
    private List<Participant> participants;

    private String language;
    private List<String> tags;
}
