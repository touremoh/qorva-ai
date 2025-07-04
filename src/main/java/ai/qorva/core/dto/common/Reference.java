package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reference implements Serializable {
    private String name;
    private String position;
    private String company;
    private ReferenceContact contact;
}
