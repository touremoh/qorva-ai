package ai.qorva.core.it;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

/** Finds dependent documents whose parent no longer exists (the thing delete cascades must prevent). */
final class OrphanCheck {

	private OrphanCheck() {
	}

	static List<String> find(MongoTemplate mongo) {
		var orphans = new ArrayList<String>();
		check(mongo, orphans, "chat_messages", "chatId", "chats");
		check(mongo, orphans, "chats", "context.cvId", "cvs");
		check(mongo, orphans, "chats", "context.matchingReportId", "matching_reports");
		check(mongo, orphans, "matching_reports", "candidateInfo.candidateId", "cvs");
		check(mongo, orphans, "matching_reports", "jobPostId", "job_posts");
		check(mongo, orphans, "candidate_outreach", "cvId", "cvs");
		check(mongo, orphans, "candidate_update_requests", "cvId", "cvs");
		for (var note : mongo.getCollection("notes").find()) {
			var parent = "CV".equals(note.getString("targetType")) ? "cvs" : "matching_reports";
			if (!exists(mongo, parent, note.get("targetId"))) {
				orphans.add("notes " + note.get("_id") + " → " + parent + " " + note.get("targetId"));
			}
		}
		return orphans;
	}

	private static void check(MongoTemplate mongo, List<String> orphans, String collection, String field, String parent) {
		for (var doc : mongo.getCollection(collection).find(new Document(field, new Document("$exists", true).append("$ne", null)))) {
			var ref = field.contains(".") ? doc.getEmbedded(List.of(field.split("\\.")), Object.class) : doc.get(field);
			if (!exists(mongo, parent, ref)) {
				orphans.add(collection + " " + doc.get("_id") + " → " + parent + " " + ref);
			}
		}
	}

	private static boolean exists(MongoTemplate mongo, String collection, Object ref) {
		if (ref == null) return true;
		var id = ref instanceof ObjectId oid ? oid : ObjectId.isValid(ref.toString()) ? new ObjectId(ref.toString()) : null;
		var filter = id != null
			? new Document("$or", List.of(new Document("_id", id), new Document("_id", ref.toString())))
			: new Document("_id", ref.toString());
		return mongo.getCollection(collection).countDocuments(filter) > 0;
	}
}
