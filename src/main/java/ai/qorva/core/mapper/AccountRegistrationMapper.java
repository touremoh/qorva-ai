package ai.qorva.core.mapper;

import ai.qorva.core.dto.AccountRegistrationDTO;
import ai.qorva.core.dto.CompanyDTO;
import ai.qorva.core.dto.UserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountRegistrationMapper {
	@Mapping(target = "rawPassword", source = "accountRegistrationDTO.password")
	@Mapping(target = "companyId", source = "companyDTO.id")
	@Mapping(target = "firstName", source = "accountRegistrationDTO.firstName")
	@Mapping(target = "lastName", source = "accountRegistrationDTO.lastName")
	@Mapping(target = "email", source = "accountRegistrationDTO.email")
	@Mapping(target = "languageCode", source = "accountRegistrationDTO.languageCode")

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "lastUpdatedAt", ignore = true)
	UserDTO map(AccountRegistrationDTO accountRegistrationDTO, CompanyDTO companyDTO);
}
