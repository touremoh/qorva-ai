package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum StrictnessEnum {
    STRICT("strict"),
    MEDIUM("medium"),
    RELAXED("relaxed");

    StrictnessEnum(String value) {
        this.value = value;
    }
    private final String value;
}
