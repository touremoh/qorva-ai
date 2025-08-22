package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.repository.TenantRepository;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.TenantMapper;
import ai.qorva.core.qbe.TenantQueryBuilder;
import ai.qorva.core.utils.QorvaUtils;
import io.jsonwebtoken.lang.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

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
		// Check tenant name was provided
		if (!Strings.hasText(dto.getTenantName())) {
			throw new QorvaException("Tenant name is null or empty");
		}

		// Check subscription info was provided
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
			QorvaUtils.merge(newSubscriptionInfo, oldSubscriptionInfo);
			dto.setSubscriptionInfo(newSubscriptionInfo);
		}
		this.mapper.merge(dto, foundTenant);
	}

	// override preProcessFindOneByData to include tenantId
	@Override
	protected void preProcessFindOneByData(TenantDTO dto) {
		if (!Strings.hasText(dto.getTenantId())
			&& !Strings.hasText(dto.getStripeCustomerId())
			&& Objects.isNull(dto.getSubscriptionInfo())) {
			log.warn("At least one of these fields must not be empty: Tenant id, Stripe customer id, SubscriptionInfo");
			throw new RuntimeException("Tenant id, Stripe customer id, SubscriptionInfo is empty while trying to find one by data");
		}
	}

	@Override
	protected void preProcessDeleteOneById(String id, String tenantId) throws QorvaException {
		// Check if a resource exists
		Optional.ofNullable(this.findOneById(id)).orElseThrow(() -> new QorvaException("Resource not found with id: " + id));
	}
}
