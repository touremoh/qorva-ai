package ai.qorva.core.dto.common;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageMetadata {
    private List<String> citations;
    private Boolean unsafe;
}