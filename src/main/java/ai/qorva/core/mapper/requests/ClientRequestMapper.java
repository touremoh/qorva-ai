package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.ClientDTO;
import java.util.Collections;

import ai.qorva.core.dto.common.ClientContact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface ClientRequestMapper extends QorvaRequestMapper<ClientDTO> {

	@Mapping(target = "domains", ignore = true)
	@Mapping(target = "contacts", ignore = true)
	@Mapping(target = "externalIds", ignore = true)
	ClientDTO map(Map<String, Object> params);

	default String toString(Object value) {
		return String.valueOf(value);
	}

	default List<String> toStringList(Object value) {
		return Collections.emptyList();
	}

	default List<ClientContact> toClientContactList(Object value) {
		return Collections.emptyList();
	}

	default Map<String, String> toMap(Object value) {
		return Map.of();
	}
}
