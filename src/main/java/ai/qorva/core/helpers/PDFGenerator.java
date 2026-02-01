package ai.qorva.core.helpers;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.common.*;
import ai.qorva.core.exception.QorvaException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.owasp.encoder.Encode;

import static java.util.Optional.ofNullable;

@UtilityClass
public class PDFGenerator {

	public byte[] generateCV(CVDTO data, String languageCode) throws QorvaException {
		Objects.requireNonNull(languageCode, "language is required");
		Objects.requireNonNull(data, "CV data is required");

		String templatePath = "templates/CV/" + languageCode + "-cv-template.html";
		String templateHtml = readClasspathUtf8(templatePath);

		Map<String, String> replacements = new LinkedHashMap<>();

		ofNullable(data.getPersonalInformation()).ifPresent(personInfo -> {
			replacements.put("{{FULL_NAME}}", escapeHtml(personInfo.getName()));
			replacements.put("{{fullName}}", escapeHtml(personInfo.getName()));
			replacements.put("{{jobTitle}}", escapeHtml(personInfo.getRole()));
			replacements.put("{{profileSummary}}", escapeHtml(personInfo.getSummary()));

			if (Objects.nonNull(personInfo.getContact())) {
				var contact = personInfo.getContact();
				replacements.put("{{phone}}", escapeHtml(contact.getPhone()));
				replacements.put("{{email}}", escapeHtml(contact.getEmail()));

				if (Objects.nonNull(contact.getSocialLinks())) {
					replacements.put("{{website}}", escapeHtml(contact.getSocialLinks().getWebsite()));
				}
			}
		});

		replacements.put("{{educationItems}}", getEducationItems(data.getEducation()));
		replacements.put("{{workExperienceItems}}", getWorkExperienceItems(data.getWorkExperience()));
		replacements.put("{{referenceItems}}", getReferenceItems(data.getReferences()));
		replacements.put("{{languagesItems}}", getLanguagesItems(Objects.nonNull(data.getSkillsAndQualifications()) ? data.getSkillsAndQualifications().getLanguages() : List.of()));
		replacements.put("{{skillsItems}}", getSkillsItems(data.getKeySkills()));

		String finalHtml = applyReplacements(templateHtml, replacements);

		// Important: baseUri must point to the "templates/CV/" directory so your CSS relative link works:
		// <link rel="stylesheet" href="css/cv-template-styles.css">
		String baseUri = classpathDirBaseUri("templates/CV/");

		return renderPdf(finalHtml, baseUri);
	}

	private String readClasspathUtf8(String path) {
		try (var in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalArgumentException("Template not found or unreadable: " + path, e);
		}
	}

	private String applyReplacements(String html, Map<String, String> replacements) {
		String out = html;
		for (var entry : replacements.entrySet()) {
			out = out.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
		}
		return out;
	}

	private String classpathDirBaseUri(String dirPath) {
		try {
			URL url = new ClassPathResource(dirPath).getURL();
			// Ensure trailing slash for correct relative resolution
			String uri = url.toExternalForm();
			return uri.endsWith("/") ? uri : (uri + "/");
		} catch (IOException e) {
			throw new IllegalStateException("Failed to resolve baseUri for: " + dirPath, e);
		}
	}

	private String escapeHtml(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#x27;");
	}

	private String normalizeForOpenHtmlToPdf(String html) {
		if (html == null) return "";

		// Remove UTF-8 BOM if present
		if (!html.isEmpty() && html.charAt(0) == '\uFEFF') {
			html = html.substring(1);
		}

		// Find first real tag opener
		int firstLt = html.indexOf('<');
		if (firstLt == -1) {
			throw new IllegalArgumentException("HTML contains no '<' character");
		}

		// Drop anything before it (backticks, whitespace, log markers, etc.)
		html = html.substring(firstLt);

		// Also drop anything AFTER the final closing html tag that might have been appended
		int lastHtmlClose = html.lastIndexOf("</html>");
		if (lastHtmlClose != -1) {
			html = html.substring(0, lastHtmlClose + "</html>".length());
		}

		return html;
	}


	private byte[] renderPdf(String html, String baseUri) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.withHtmlContent(normalizeForOpenHtmlToPdf(html), baseUri);
			builder.useFastMode();
			builder.toStream(baos);
			builder.run();
			return baos.toByteArray();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to render CV PDF", e);
		}
	}

	private String getComponent(String componentName) {
		return readClasspathUtf8("templates/CV/components/" + componentName);
	}

	private String getEducationItems(List<Education> educationItems) {
		if (Objects.isNull(educationItems) || educationItems.isEmpty()) return "";

		var strEducationItems = new StringBuilder();

		for (var education : educationItems) {
			var educationComponent = getComponent("education-item.html");
			educationComponent = educationComponent.replace("{{eduPeriod}}", escapeHtml(education.getYear()));
			educationComponent = educationComponent.replace("{{eduSchool}}", escapeHtml(education.getInstitution()));
			educationComponent = educationComponent.replace("{{eduDegree}}", escapeHtml(education.getDegree()));
			educationComponent = educationComponent.replace("{{eduField}}", escapeHtml(education.getFieldOfStudy()));

			strEducationItems.append(educationComponent);
		}
		return strEducationItems.toString();
	}

	private String getWorkExperienceItems(List<WorkExperience> workExperienceItems) {
		if (Objects.isNull(workExperienceItems) || workExperienceItems.isEmpty()) return "";

		var strWorkExperienceItems = new StringBuilder();

		Function<WorkExperience, String> workXpPeriod = (workXP) -> {
			if (Objects.isNull(workXP.getFrom()) && Objects.isNull(workXP.getTo())) {
				return "N/A";
			}

			if (Objects.nonNull(workXP.getFrom()) && Objects.isNull(workXP.getTo())) {
				return workXP.getFrom() + " - " + "Present";
			}

			return workXP.getFrom() + " - " + workXP.getTo();
		};

		Function<List<Activity>, String> workXpActivities = (activities) -> {
			var strActivities = new StringBuilder();
			activities.forEach(activity -> {
				var strTasks = new StringBuilder();
				for (var task : activity.getTasks()) {
					strTasks.append(" <li> ").append(Encode.forXmlContent(task)).append(" </li> ");
				}

				var workXpActivitiesProjectTasksComponent = getComponent("work-experience-project-activities-task-item.html");
				workXpActivitiesProjectTasksComponent = workXpActivitiesProjectTasksComponent.replace("{{projectName}}", escapeHtml(activity.getProject()));
				workXpActivitiesProjectTasksComponent = workXpActivitiesProjectTasksComponent.replace("{{activitiesTasks}}", strTasks.toString());

				strActivities.append(workXpActivitiesProjectTasksComponent);
			});
			return strActivities.toString();
		};

		workExperienceItems.forEach(workExperience -> {
			var workExperienceComponent = getComponent("work-experience-item.html");
			workExperienceComponent = workExperienceComponent.replace("{{expCompany}}", escapeHtml(workExperience.getCompany()));
			workExperienceComponent = workExperienceComponent.replace("{{expPeriod}}", escapeHtml(workXpPeriod.apply(workExperience)));
			workExperienceComponent = workExperienceComponent.replace("{{expRole}}", escapeHtml(workExperience.getPosition()));
			workExperienceComponent = workExperienceComponent.replace("{{expBullets}}", workXpActivities.apply(workExperience.getActivities()));

			strWorkExperienceItems.append(workExperienceComponent);
		});

		return strWorkExperienceItems.toString();
	}

	private String getReferenceItems(List<Reference> referenceItems) {
		if (Objects.isNull(referenceItems) || referenceItems.isEmpty()) return "";

		var strReferenceItems = new StringBuilder();

		referenceItems.forEach(reference -> {
			var referenceComponent = getComponent("reference-item.html");
			referenceComponent = referenceComponent.replace("{{refName}}", escapeHtml(reference.getName()));
			referenceComponent = referenceComponent.replace("{{refCompanyAndTitle}}", escapeHtml(reference.getCompany() + ", " + reference.getPosition()));
			referenceComponent = referenceComponent.replace("{{refPhone}}", escapeHtml(reference.getContact() != null ? reference.getContact().getPhone() : ""));
			referenceComponent = referenceComponent.replace("{{refEmail}}", escapeHtml(reference.getContact() != null ? reference.getContact().getEmail() : ""));

			strReferenceItems.append(referenceComponent);
		});
		return strReferenceItems.toString();
	}

	private String getSkillsItems(List<KeySkill> skillsItems) {
		if (Objects.isNull(skillsItems) || skillsItems.isEmpty()) return "";

		var strSkillsItems = new StringBuilder();

		Function<List<String>, String> skillsList = (skills) -> {
			var strSkills = new StringBuilder();
			skills.forEach(skill -> strSkills.append(" <li> ").append(skill).append(" </li> "));
			return strSkills.toString();
		};

		skillsItems.forEach(skill -> {
			var skillsComponent = getComponent("skill-item.html");
			skillsComponent = skillsComponent.replace("{{skillCategory}}", escapeHtml(skill.getCategory()));
			skillsComponent = skillsComponent.replace("{{skillsLiItems}}", skillsList.apply(skill.getSkills()));

			strSkillsItems.append(skillsComponent);
		});

		return strSkillsItems.toString();
	}

	private String getLanguagesItems(List<Language> languageItems) {
		if (Objects.isNull(languageItems) || languageItems.isEmpty()) return "";

		var strLanguageItems = new StringBuilder();

		languageItems.forEach(language -> {
			var languageComponent = "<li> " + language.getLanguage() + " - " + language.getProficiency().getWritten() + " </li> ";
			strLanguageItems.append(languageComponent);
		});
		return strLanguageItems.toString();
	}
}
