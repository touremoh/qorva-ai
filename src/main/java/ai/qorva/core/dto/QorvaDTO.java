package ai.qorva.core.dto;

import java.io.Serializable;

public interface QorvaDTO extends Serializable {
	String getId();
	void setId(String id);
	String getTenantId();
	void setTenantId(String tenantId);
}
