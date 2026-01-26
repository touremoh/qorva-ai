package ai.qorva.core.migrations;

import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.helpers.UserAuthoritiesHelper;
import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@ChangeUnit(id = "V010PopulateDefaultAuthoritiesForAllUsersChangeLog", order = "010", author = "qorva")
public class V010PopulateDefaultAuthoritiesForAllUsersChangeLog extends AbstractQorvaDbMigration {

	protected static final String COLLECTION_NAME = "Users";
	protected final UserRepository userRepository;

	@Autowired
	public V010PopulateDefaultAuthoritiesForAllUsersChangeLog(UserRepository userRepository) {
		super();
		this.userRepository = userRepository;
	}

	@Execution
	public void execute(MongoDatabase db) {
		log.info("Populating default authorities for all users without authorities");

		var  users = userRepository.findAll();
		users.forEach(user -> {
			if (Objects.isNull(user.getAuthorities()) ||  user.getAuthorities().isEmpty()) {
				user.setAuthorities(UserAuthoritiesHelper.createAuthorities());
				userRepository.save(user);
			}
		});
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("Change Log ID = (V010PopulateDefaultAuthoritiesForAllUsersChangeLog) - execution failed");
	}
}
