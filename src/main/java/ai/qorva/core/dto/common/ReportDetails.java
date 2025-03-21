package ai.qorva.core.dto.common;

import com.microsoft.graph.models.Person;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportDetails implements Serializable {
	private String detailsID;
	private String jobTitle;
	private String candidateName;
	private String candidateEmail;
	private String candidatePhone;
	private String candidateCVID;
	private String candidateRole;
	private SkillsMatch skillsMatch;
	private ExceedsRequirements exceedsRequirements;
	private LackingSkills lackingSkills;
	private ExperienceAlignment experienceAlignment;
	private OverallSummary overallSummary;
	private InterviewQuestions interviewQuestions;
}
