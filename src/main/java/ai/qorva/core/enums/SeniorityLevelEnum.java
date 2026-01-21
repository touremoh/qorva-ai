package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum SeniorityLevelEnum {
    JUNIOR("junior"),
    MID("mid"),
    SENIOR("senior");

    SeniorityLevelEnum(String value) {
        this.value = value;
    }
    private final String value;
}
