package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum SkillImportanceEnum {
    MANDATORY("mandatory"),
    IMPORTANT("important"),
    NICE_TO_HAVE("nice_to_have");

    SkillImportanceEnum(String value) {
        this.value = value;
    }
    private final String value;
}
