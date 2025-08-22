package ai.qorva.core.dto;

import java.io.Serializable;

public interface QorvaDTO extends Serializable {
	String getId();
	String getTenantId();
	void setTenantId(String tenantId);
}
