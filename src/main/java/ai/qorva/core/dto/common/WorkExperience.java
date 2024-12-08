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
public class WorkExperience {
    private String company;
    private String website;
    private String location;
    private String from;
    private String to;
    private String position;
    private List<Activity> activities;
    private List<String> achievements;
    private List<String> toolsAndTechnologies;
}
