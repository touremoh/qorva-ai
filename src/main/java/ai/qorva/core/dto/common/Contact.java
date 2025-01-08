package ai.qorva.core.dto.common;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Contact implements Serializable {
    private String phone;
    private String email;
    private SocialLinks socialLinks;
}
