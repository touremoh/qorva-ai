package ai.qorva.core.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a CV document into images for vision extraction. PDFs are rendered page by page;
 * .docx files contribute their embedded pictures (POI cannot rasterize whole pages).
 * Bounded on purpose: vision passes read the first pages, not a 40-page annex.
 */
@Slf4j
@UtilityClass
public class CVPageImageRenderer {

	public static final int MAX_PDF_PAGES = 3;
	public static final int MAX_DOCX_PICTURES = 5;
	private static final float RENDER_DPI = 150f;

	public record PageImage(byte[] bytes, String mimeType) {}

	/** Best-effort: rendering failures return an empty list — the caller falls back to text-only. */
	public List<PageImage> render(String filename, String contentType, byte[] bytes) {
		try {
			if (isPdf(filename, contentType)) {
				return renderPdf(bytes);
			}
			if (isDocx(filename, contentType)) {
				return extractDocxPictures(bytes);
			}
		} catch (Exception | NoClassDefFoundError e) {
			log.warn("Could not render {} for vision extraction: {}", filename, e.toString());
		}
		return List.of();
	}

	private boolean isPdf(String filename, String contentType) {
		return (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf"))
			|| (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf"));
	}

	private boolean isDocx(String filename, String contentType) {
		return (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("wordprocessingml"))
			|| (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".docx"));
	}

	private List<PageImage> renderPdf(byte[] bytes) throws Exception {
		var pages = new ArrayList<PageImage>();
		try (var document = Loader.loadPDF(bytes)) {
			var renderer = new PDFRenderer(document);
			int pageCount = Math.min(document.getNumberOfPages(), MAX_PDF_PAGES);
			for (int i = 0; i < pageCount; i++) {
				var image = renderer.renderImageWithDPI(i, RENDER_DPI);
				var out = new ByteArrayOutputStream();
				ImageIO.write(image, "png", out);
				pages.add(new PageImage(out.toByteArray(), "image/png"));
			}
		}
		return pages;
	}

	private List<PageImage> extractDocxPictures(byte[] bytes) throws Exception {
		var pictures = new ArrayList<PageImage>();
		try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
			for (var picture : document.getAllPictures()) {
				if (pictures.size() >= MAX_DOCX_PICTURES) {
					break;
				}
				var ext = picture.suggestFileExtension();
				if ("png".equalsIgnoreCase(ext)) {
					pictures.add(new PageImage(picture.getData(), "image/png"));
				} else if ("jpeg".equalsIgnoreCase(ext) || "jpg".equalsIgnoreCase(ext)) {
					pictures.add(new PageImage(picture.getData(), "image/jpeg"));
				}
			}
		}
		return pictures;
	}
}
