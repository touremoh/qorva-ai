package ai.qorva.core.dto.common;

import java.util.List;

public record QuestionnaireDetails(
    List<String> skillsBasedQuestions,
    List<String> strengthBasedQuestions,
    List<String> gapExplorationQuestions
) {}
