// Entity: ScreeningReport
package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.ReportDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ScreeningReports")
public class ScreeningReport implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String companyId;

    private String reportName;
    private List<ReportDetails> reportDetails;
    private String status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;
}
