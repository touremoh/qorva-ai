package ai.qorva.core.service.cascade;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.repository.ChatMessagesRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resume chats (and their messages) are about a CV, a job post and optionally a report. */
@Component
public class ChatCascade implements CascadeParticipant {

	private final ChatsRepository chats;
	private final ChatMessagesRepository messages;

	public ChatCascade(ChatsRepository chats, ChatMessagesRepository messages) {
		this.chats = chats;
		this.messages = messages;
	}

	@Override
	public List<Deleted> onParentsDeleted(CascadeResource parent, String tenantId, Collection<String> parentIds) {
		var chatIds = parentIds.stream()
			.flatMap(id -> switch (parent) {
				case CV -> chats.findByTenantIdAndContextCvId(tenantId, id).stream();
				case JOB_POST -> chats.findByTenantIdAndContextJobPostId(tenantId, id).stream();
				case MATCHING_REPORT -> chats.findByTenantIdAndContextMatchingReportId(tenantId, id).stream();
			})
			.map(Chat::getId)
			.distinct()
			.toList();
		if (chatIds.isEmpty()) {
			return List.of();
		}
		// Messages first, while their chats are still resolvable — deleting chats alone orphaned them.
		var deletedMessages = messages.deleteByTenantIdAndChatIdIn(tenantId, chatIds);
		var deletedChats = chats.deleteByTenantIdAndIdIn(tenantId, chatIds);
		return List.of(Deleted.of("chat_messages", deletedMessages), Deleted.of("chats", deletedChats));
	}

	@Override
	public Map<String, Long> onTenantPurge(String tenantId, PurgeScope scope) {
		var counts = new LinkedHashMap<String, Long>();
		counts.put("chat_messages", messages.deleteByTenantId(tenantId));
		counts.put("chats", chats.deleteByTenantId(tenantId));
		return counts;
	}
}
