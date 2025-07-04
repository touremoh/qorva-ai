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
public class PersonalInformation implements Serializable {
    private String name;
    private Contact contact;
    private String role;
    private Availability availability;
    private String summary;
}
