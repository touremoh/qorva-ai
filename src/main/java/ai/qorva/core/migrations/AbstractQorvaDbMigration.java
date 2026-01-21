package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractQorvaDbMigration {

	protected static final String BASE_PATH = "/db/migrations/";

	public void createCollection(MongoDatabase db, String collectionName, String executionMessage, String fileName) {
		log.info(executionMessage);

		// Create the collection
		db.createCollection(collectionName);

		// Load data
		this.updateCollection(db, fileName, executionMessage);
	}

	public void updateCollection(MongoDatabase db, String fileName, String executionMessage) {
		log.info(executionMessage);
		db.runCommand(BsonDocument.parse(this.readClasspath(BASE_PATH.concat(fileName))));
	}

	public void dropCollection(MongoDatabase db, String collectionName) {
		log.info("Dropping collection: {}", collectionName);
		db.getCollection(collectionName).drop();
	}

	protected String readClasspath(String path) {
		try (var in = getClass().getResourceAsStream(path);
			var br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8))) {
			return br.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			throw new IllegalStateException("Cannot read resource: " + path, e);
		}
	}
}
