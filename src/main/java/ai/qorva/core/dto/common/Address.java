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
public class Address implements Serializable {
    private String streetNumber;
    private String streetName;
    private String streetType;
    private String unit;
    private String city;
    private String state;
    private String zipCode;
    private String country;
}
