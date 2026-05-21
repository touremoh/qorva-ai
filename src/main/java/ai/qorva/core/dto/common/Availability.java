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
public class Availability implements Serializable {
    private Boolean openToWork;
    private String status;
    private String availableFrom;
    private Integer noticePeriodDays;
    private List<String> interviewAvailability;
    private List<String> preferredWorkTypes;
    private List<String> preferredContractTypes;
    private Boolean willingToRelocate;
    private Boolean remoteOnly;
}
