package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
