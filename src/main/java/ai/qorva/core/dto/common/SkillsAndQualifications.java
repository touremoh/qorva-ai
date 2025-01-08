package ai.qorva.core.dto.common;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkillsAndQualifications implements Serializable {
    private List<String> technicalSkills;
    private List<String> softSkills;
    private List<Language> languages;
}
