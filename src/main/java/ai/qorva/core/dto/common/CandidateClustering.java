package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandidateClustering implements Serializable {
    private String primaryCluster;
    private List<String> secondaryClusters;
    private List<String> functionalExpertise;
    private String skillDepth;
    private List<String> businessImpact;
    private List<String> industryDomains;
    private List<String> environmentFit;
    private String seniorityLevel;
    private String leadershipAndInfluence;
    private String learningVelocity;
    private Double clusterConfidenceScore;
    private String clusterReasoning;
}
