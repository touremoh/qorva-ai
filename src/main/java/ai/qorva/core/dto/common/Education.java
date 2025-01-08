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
public class Education implements Serializable  {
    private String year;
    private String institution;
    private String degree;
    private String fieldOfStudy;
    private List<String> achievements;
}
