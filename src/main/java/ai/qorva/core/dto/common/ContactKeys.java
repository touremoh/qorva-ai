package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Normalised contact values used only to find duplicate resumes (see ContactNormalizer): the
 * same person is recognised whatever the letter case of the email or the formatting of the phone.
 * Derived on every write; never shown to users.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactKeys implements Serializable {
    /** Trimmed, lower-cased email. */
    private String email;
    /** E.164 (e.g. +32470123456), or {@code local:<digits>} when the number's country is unknown. */
    private String phone;
}
