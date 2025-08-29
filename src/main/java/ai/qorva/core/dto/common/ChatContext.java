package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatContext {
    private String cvId;          // ObjectId string
    private String jobPostId;     // ObjectId string
    private String resumeMatchId; // ObjectId string or null
}
