package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Reference to the original CV file stored in S3. The binary itself is never persisted in MongoDB.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentInfo implements Serializable {
    private String s3Key;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
}
