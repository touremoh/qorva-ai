package ai.qorva.core.dto.common;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestions {
    private List<String> skillsBasedQuestions;
    private List<String> strengthBasedQuestions;
    private List<String> gapExplorationQuestions;
}
