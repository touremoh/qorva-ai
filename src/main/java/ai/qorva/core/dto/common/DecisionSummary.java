package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DecisionSummary implements Serializable {
    private String reportHeadline;
    private Double finalScore;
    private String detailedSummary;
    private String shortVerdict;
    private String recommendation;
    private String confidenceLevel;
}
