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
public class MissingSkills implements Serializable {
    private String summary;
    private List<SkillEntry> skills;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillEntry implements Serializable {
        private String skill;
        private String importance;
    }
}
