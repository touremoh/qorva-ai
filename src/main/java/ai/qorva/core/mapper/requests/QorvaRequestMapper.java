package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.QorvaDTO;

import java.util.Map;

public interface QorvaRequestMapper<D extends QorvaDTO> {

	/**
	 * Convert a tenant ID to a DTO
	 * @param tenantId params
	 * @return DTO
	 */
	D toDtoFromTenantID(String tenantId);

	/**
	 * Convert a map to a DTO
	 * @param params params
	*/
	D toDto(Map<String, String> params);
}
