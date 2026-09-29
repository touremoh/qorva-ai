package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.common.KeySkill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the model sees of a CV. Never contact details: outbound tools resolve addresses server-side,
 * and CV text is candidate-written, so it is handed over as data, not instructions.
 */
final class CvProjections {

	private CvProjections() {
	}

	static String name(CVDTO cv) {
		return cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null;
	}

	static List<String> topSkills(CVDTO cv, int max) {
		if (cv.getKeySkills() == null) return List.of();
		return cv.getKeySkills().stream()
			.filter(Objects::nonNull)
			.map(KeySkill::getSkills)
			.filter(Objects::nonNull)
			.flatMap(List::stream)
			.distinct()
			.limit(max)
			.toList();
	}

	static Map<String, Object> summary(CVDTO cv) {
		var out = new LinkedHashMap<String, Object>();
		out.put("cvId", cv.getId());
		out.put("name", name(cv));
		out.put("role", cv.getPersonalInformation() != null ? cv.getPersonalInformation().getRole() : null);
		out.put("seniority", cv.getCandidateClustering() != null ? cv.getCandidateClustering().getSeniorityLevel() : null);
		out.put("yearsOfExperience", cv.getNbYearsOfExperience());
		out.put("location", location(cv));
		out.put("topSkills", topSkills(cv, 8));
		out.put("tags", cv.getTags() != null ? cv.getTags() : List.of());
		out.put("archived", Boolean.TRUE.equals(cv.getArchived()));
		return out;
	}

	static Map<String, Object> detail(CVDTO cv) {
		var out = summary(cv);
		out.put("topSkills", topSkills(cv, 30));
		out.put("profileSummary", ToolArgs.truncate(cv.getCandidateProfileSummary(), 1200));
		var info = cv.getPersonalInformation();
		if (info != null && info.getAvailability() != null) {
			out.put("openToWork", info.getAvailability().getOpenToWork());
			out.put("availabilityStatus", info.getAvailability().getStatus());
		}
		if (cv.getCandidateClustering() != null) {
			out.put("primaryCluster", cv.getCandidateClustering().getPrimaryCluster());
			out.put("industries", cv.getCandidateClustering().getIndustryDomains());
		}
		if (cv.getWorkExperience() != null) {
			out.put("workExperience", cv.getWorkExperience().stream().limit(8).map(w -> {
				var row = new LinkedHashMap<String, Object>();
				row.put("position", w.getPosition());
				row.put("company", w.getCompany());
				row.put("from", w.getFrom());
				row.put("to", w.getTo());
				return row;
			}).toList());
		}
		if (cv.getEducation() != null) {
			out.put("education", cv.getEducation().stream().limit(5).map(e -> {
				var row = new LinkedHashMap<String, Object>();
				row.put("degree", e.getDegree());
				row.put("fieldOfStudy", e.getFieldOfStudy());
				row.put("institution", e.getInstitution());
				return row;
			}).toList());
		}
		return out;
	}

	private static String location(CVDTO cv) {
		var info = cv.getPersonalInformation();
		if (info == null || info.getContact() == null || info.getContact().getAddress() == null) return null;
		var address = info.getContact().getAddress();
		var parts = java.util.stream.Stream.of(address.getCity(), address.getCountry())
			.filter(s -> s != null && !s.isBlank()).toList();
		return parts.isEmpty() ? null : String.join(", ", parts);
	}
}
