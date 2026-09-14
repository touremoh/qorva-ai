package ai.qorva.core.service;

import ai.qorva.core.enums.NoteTargetTypeEnum;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * SpEL entry point for {@code @PreAuthorize} on the notes API: the authority that gates a note is
 * the one gating its target (VIEW_CV/MODIFY_CV for CV notes, VIEW_REPORT/MODIFY_REPORT for report
 * notes), and the target type is a request value, so the check cannot be a literal action name.
 * An unknown type is refused here rather than answered 400 — the service repeats the parse.
 */
@Service("noteAccess")
public class NoteAccessManager {

	private final QorvaApiAccessManager accessManager;

	public NoteAccessManager(QorvaApiAccessManager accessManager) {
		this.accessManager = accessManager;
	}

	public boolean canRead(Authentication authentication, String targetType) {
		var type = NoteTargetTypeEnum.fromValue(targetType);
		return type != null && accessManager.hasPermission(authentication, type.getReadAction().getValue());
	}

	public boolean canWrite(Authentication authentication, String targetType) {
		var type = NoteTargetTypeEnum.fromValue(targetType);
		return type != null && accessManager.hasPermission(authentication, type.getWriteAction().getValue());
	}

	public boolean canWrite(Authentication authentication, NoteTargetTypeEnum type) {
		return type != null && accessManager.hasPermission(authentication, type.getWriteAction().getValue());
	}
}
