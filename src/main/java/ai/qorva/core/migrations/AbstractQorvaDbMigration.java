package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractQorvaDbMigration {

	protected static final String BASE_PATH = "/db/migrations/";

	/**
	 * Ensures the collection exists, then applies its DDL file. Creation is skipped when the
	 * collection is already there — Spring Data creates a collection implicitly on the first
	 * write, so any environment that ran a feature before its changeunit was written already
	 * has it, and an unconditional createCollection fails with CollectionAlreadyExists (48)
	 * and takes the whole migration run down with it.
	 */
	public void createCollection(MongoDatabase db, String collectionName, String executionMessage, String fileName) {
		log.info(executionMessage);

		if (collectionExists(db, collectionName)) {
			log.info("Collection {} already exists — applying validator only", collectionName);
		} else {
			db.createCollection(collectionName);
		}

		// Load data
		this.updateCollection(db, fileName, executionMessage);
	}

	protected boolean collectionExists(MongoDatabase db, String collectionName) {
		return db.listCollectionNames().into(new ArrayList<>()).contains(collectionName);
	}

	/**
	 * Creates a collection that carries no DDL file, skipping it when already present.
	 * Index creation by the caller is idempotent as long as name and keys are unchanged.
	 */
	protected void createCollectionIfAbsent(MongoDatabase db, String collectionName) {
		if (collectionExists(db, collectionName)) {
			log.info("Collection {} already exists — creating indexes only", collectionName);
			return;
		}
		db.createCollection(collectionName);
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
