package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.repository.TenantRepository;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.TenantMapper;
import ai.qorva.core.dao.querybuilder.TenantQueryBuilder;
import ai.qorva.core.utils.QorvaUtils;
import io.jsonwebtoken.lang.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class TenantService extends AbstractQorvaService<TenantDTO,Tenant> {

	@Autowired
	protected TenantService(TenantRepository repository, TenantMapper mapper, TenantQueryBuilder queryBuilder) {
		super(repository, mapper, queryBuilder);
	}

	@Override
	protected void preProcessCreateOne(TenantDTO dto) throws QorvaException {
		if (!Strings.hasText(dto.getTenantName())) {
			throw new QorvaException("Tenant name is null or empty");
		}
		if (!Strings.hasText(dto.getOrganizationId())) {
			throw new QorvaException("organizationId is null or empty");
		}
		if (Objects.isNull(dto.getSubscriptionInfo())) {
			throw new QorvaException("SubscriptionInfo is null or empty");
		}
	}

	@Override
	protected void preProcessUpdateOne(String id, TenantDTO dto) throws QorvaException {
		super.preProcessUpdateOne(id, dto);

		// Find the resource to update
		var foundTenant = this.findOneById(id);

		// Merge the new resource data into the existing one
		var newSubscriptionInfo = dto.getSubscriptionInfo();
		var oldSubscriptionInfo = foundTenant.getSubscriptionInfo();

		if (Objects.isNull(newSubscriptionInfo)) {
			dto.setSubscriptionInfo(oldSubscriptionInfo);
		} else {
			QorvaUtils.patchLeft(newSubscriptionInfo, oldSubscriptionInfo);
			dto.setSubscriptionInfo(newSubscriptionInfo);
		}
		this.mapper.merge(dto, foundTenant);
	}

	@Override
	protected void preProcessFindOneByData(TenantDTO dto) {
		if (!Strings.hasText(dto.getTenantId())
			&& !Strings.hasText(dto.getStripeCustomerId())
			&& !Strings.hasText(dto.getOrganizationId())
			&& Objects.isNull(dto.getSubscriptionInfo())) {
			log.warn("At least one of these fields must not be empty: tenantId, stripeCustomerId, organizationId, subscriptionInfo");
			throw new RuntimeException("No search criteria provided while trying to find tenant by data");
		}
	}

	@Override
	protected void preProcessDeleteOneById(String id, String tenantId) throws QorvaException {
		// Check if a resource exists
		Optional.ofNullable(this.findOneById(id)).orElseThrow(() -> new QorvaException("Resource not found with id: " + id));
	}
}
