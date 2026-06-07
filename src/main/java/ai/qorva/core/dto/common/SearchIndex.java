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
public class SearchIndex implements Serializable {
    private List<String> roles;
    private List<String> skills;
    private List<String> industries;
}
