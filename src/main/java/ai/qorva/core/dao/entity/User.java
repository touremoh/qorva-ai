package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.UserPermission;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Users")
public class User implements QorvaEntity {

    @Id
    private String id;

    private String firstName;
    private String lastName;

    @TextIndexed(weight = 5)
    private String email;

    private String encryptedPassword;

    private String userAccountStatus; // Expected: USER_ACTIVE, USER_INACTIVE, USER_LOCKED

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    List<UserPermission> permissions;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;
}
