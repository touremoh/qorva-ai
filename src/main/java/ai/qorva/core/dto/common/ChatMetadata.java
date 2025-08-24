package ai.qorva.core.dto.common;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMetadata {
    private String language;
    private List<String> tags;
}