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
public  class CandidateInfo {
    private String candidateId;
    private String candidateName;
    private int nbYearsExperience;
    private String candidateProfileSummary;
    private List<String> skills;
}