package ai.qorva.core.dto.request;

import ai.qorva.core.dto.QorvaDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FindManyRequestCriteria implements QorvaDTO {
	private String id;
	private String tenantId;
	private int pageSize;
	private int pageNumber;
	private String searchTerms;
}
