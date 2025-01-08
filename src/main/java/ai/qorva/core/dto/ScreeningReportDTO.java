// DTO: ScreeningReportDTO
package ai.qorva.core.dto;

import ai.qorva.core.dto.common.ReportDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningReportDTO extends AbstractQorvaDTO {
    private String id;
    private String companyId;
    private String reportName;
    private List<ReportDetails> reportDetails;
    private String status;
    private Instant createdAt;
    private Instant lastUpdatedAt;
}
