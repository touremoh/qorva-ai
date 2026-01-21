package ai.qorva.core.dao.entity;


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
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ClientsReports")
public class ClientReport implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String clientId;

    private String reportType; // max 160
    private String title; // required, 3..160
    private String positionTitle; // required, 1..250
    private String preparedFor; // 1..250

    @Field(targetType = FieldType.OBJECT_ID)
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

    /**
     * Business regeneration version (validator: minimum 1).
     * If you also want optimistic locking, keep @Version on a separate field.
     */
    private Integer version;

    /**
     * Optimistic locking version for Spring Data MongoDB.
     * Remove if you don't use optimistic locking.
     */
    @Version
    private Long mongoVersion;

    private String notes;
}
