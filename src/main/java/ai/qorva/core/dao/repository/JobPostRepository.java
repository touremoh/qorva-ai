package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.JobPost;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;

@Repository
public class JobPostRepository extends AbstractQorvaRepository<JobPost> {
    public JobPostRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, JobPost.class);
    }
}
