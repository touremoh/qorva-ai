package ai.qorva.core.dto;

import ai.qorva.core.dto.common.ClientContact;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO implements QorvaDTO {

    private String id;

    private String tenantId;

    private String clientCode;

    private String name;

    private List<String> domains;

    private List<ClientContact> contacts;

    private Map<String, String> externalIds;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant archivedAt;
}