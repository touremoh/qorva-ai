package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.QorvaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public abstract class AbstractRepositoryTest<T extends QorvaEntity> {

    @Mock
    protected MongoTemplate mongoTemplate;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }
}
