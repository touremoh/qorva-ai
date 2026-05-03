package ai.qorva.core.dao.specifications;

import org.springframework.data.mongodb.core.query.Criteria;

@FunctionalInterface
public interface MongoSpecification<T> {

    Criteria toCriteria();

    default MongoSpecification<T> and(MongoSpecification<T> other) {
        return () -> new Criteria().andOperator(this.toCriteria(), other.toCriteria());
    }

    default MongoSpecification<T> or(MongoSpecification<T> other) {
        return () -> new Criteria().orOperator(this.toCriteria(), other.toCriteria());
    }

    default MongoSpecification<T> not() {
        return () -> new Criteria().norOperator(this.toCriteria());
    }

    static <T> MongoSpecification<T> where(MongoSpecification<T> spec) {
        return spec;
    }
}