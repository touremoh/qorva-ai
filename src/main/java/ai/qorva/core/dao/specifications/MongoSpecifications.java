package ai.qorva.core.dao.specifications;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;

public final class MongoSpecifications {

    private MongoSpecifications() {
    }

    public static <T> MongoSpecification<T> empty() {
        return new EmptyMongoSpecification<>();
    }

    public static <T> MongoSpecification<T> allOf(List<MongoSpecification<T>> specifications) {
        List<Criteria> criteria = specifications.stream()
            .filter(spec -> spec != null && !(spec instanceof EmptyMongoSpecification))
            .map(MongoSpecification::toCriteria)
            .toList();

        if (criteria.isEmpty()) {
            return empty();
        }

        return () -> new Criteria().andOperator(criteria.toArray(new Criteria[0]));
    }

    @SafeVarargs
    public static <T> MongoSpecification<T> allOf(MongoSpecification<T>... specifications) {
        List<MongoSpecification<T>> list = new ArrayList<>();
        for (MongoSpecification<T> spec : specifications) {
            if (spec != null) {
                list.add(spec);
            }
        }
        return allOf(list);
    }

    public static <T> MongoSpecification<T> anyOf(List<MongoSpecification<T>> specifications) {
        List<Criteria> criteria = specifications.stream()
            .filter(spec -> spec != null && !(spec instanceof EmptyMongoSpecification))
            .map(MongoSpecification::toCriteria)
            .toList();

        if (criteria.isEmpty()) {
            return empty();
        }

        return () -> new Criteria().orOperator(criteria.toArray(new Criteria[0]));
    }

    @SafeVarargs
    public static <T> MongoSpecification<T> anyOf(MongoSpecification<T>... specifications) {
        List<MongoSpecification<T>> list = new ArrayList<>();
        for (MongoSpecification<T> spec : specifications) {
            if (spec != null) {
                list.add(spec);
            }
        }
        return anyOf(list);
    }

    static final class EmptyMongoSpecification<T> implements MongoSpecification<T> {
        @Override
        public Criteria toCriteria() {
            return new Criteria();
        }
    }
}