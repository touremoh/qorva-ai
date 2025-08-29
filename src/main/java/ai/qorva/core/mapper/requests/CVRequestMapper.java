package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.CVDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface CVRequestMapper extends QorvaRequestMapper<CVDTO> {

	@Override
	@Mapping(target = "personalInformation", ignore = true)
	@Mapping(target = "keySkills", ignore = true)
	@Mapping(target = "profiles", ignore = true)
	@Mapping(target = "workExperience", ignore = true)
	@Mapping(target = "education", ignore = true)
	@Mapping(target = "certifications", ignore = true)
	@Mapping(target = "skillsAndQualifications", ignore = true)
	@Mapping(target = "projectsAndAchievements", ignore = true)
	@Mapping(target = "interestsAndHobbies", ignore = true)
	@Mapping(target = "references", ignore = true)
	@Mapping(target = "attachment", ignore = true)
	@Mapping(target = "tags", ignore = true)
	@Mapping(target = "embedding", ignore = true)
	CVDTO toDto(Map<String, String> params);
}
