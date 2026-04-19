package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.ClientReportDTO;
import ai.qorva.core.dto.common.ClientReportBranding;
import ai.qorva.core.dto.common.ClientReportFiles;
import ai.qorva.core.dto.common.ClientReportMetrics;
import ai.qorva.core.dto.common.ClientReportShortlistedCandidate;
import ai.qorva.core.enums.ClientReportStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface ClientReportRequestMapper extends QorvaRequestMapper<ClientReportDTO> {

	@Mapping(target = "branding", ignore = true)
	@Mapping(target = "shortlist", ignore = true)
	@Mapping(target = "metrics", ignore = true)
	@Mapping(target = "files", ignore = true)
	@Mapping(target = "status", ignore = true)
	ClientReportDTO map(Map<String, Object> params);

	default ClientReportBranding toClientReportBranding(Object value) {
		return null;
	}

	default ClientReportMetrics toClientReportMetrics(Object value) {
		return null;
	}

	default ClientReportFiles toClientReportFiles(Object value) {
		return null;
	}

	default ClientReportStatus toClientReportStatus(Object value) {
		return null;
	}

	default List<ClientReportShortlistedCandidate> toClientContactList(Object value) {
		return Collections.emptyList();
	}
}
