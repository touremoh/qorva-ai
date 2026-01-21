package ai.qorva.core.dto;

import ai.qorva.core.dto.common.ClientReportBranding;
import ai.qorva.core.dto.common.ClientReportFiles;
import ai.qorva.core.dto.common.ClientReportMetrics;
import ai.qorva.core.dto.common.ClientReportShortlistedCandidate;
import ai.qorva.core.enums.ClientReportStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportDTO extends AbstractQorvaDTO {

    private String id;

    private String tenantId;

    private String clientId;

    private String reportType; // max 160
    private String title; // required, 3..160
    private String positionTitle; // required, 1..250
    private String preparedFor; // 1..250
    private String preparedByUserId;

    private ClientReportBranding branding;

    private List<ClientReportShortlistedCandidate> shortlist;

    private ClientReportMetrics metrics;

    private ClientReportFiles files;

    private ClientReportStatus status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Integer version;

    @Version
    private Long mongoVersion;

    private String notes;
}