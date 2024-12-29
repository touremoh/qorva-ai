package ai.qorva.core.dto.common;


import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PersonalInformation {
    private String name;
    private Contact contact;
    private String role;
    private Availability availability;
    private String summary;
}
