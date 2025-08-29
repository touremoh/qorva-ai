package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dto.CVDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CVMapper extends AbstractQorvaMapper<CV, CVDTO> {
}
