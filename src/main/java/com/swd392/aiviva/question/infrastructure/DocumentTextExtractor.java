package com.swd392.aiviva.question.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

public class DocumentTextExtractor {
    public record PageText(String location, String text) {}

    public List<PageText> extract(String filename, byte[] bytes) throws IOException {
        String name = filename.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".pdf")) return extractPdf(bytes);
        if (name.endsWith(".pptx")) return extractPptx(bytes);
        if (name.endsWith(".docx")) return extractDocx(bytes);
        throw new IOException("Unsupported document format; accepted formats are PDF, PPTX and DOCX.");
    }

    private List<PageText> extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            List<PageText> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new PageText("page " + page, stripper.getText(document)));
            }
            return pages;
        }
    }

    private List<PageText> extractPptx(byte[] bytes) throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            List<PageText> slides = new ArrayList<>();
            for (int index = 0; index < show.getSlides().size(); index++) {
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : show.getSlides().get(index).getShapes()) {
                    if (shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank()) {
                        text.append(textShape.getText()).append('\n');
                    }
                }
                slides.add(new PageText("slide " + (index + 1), text.toString()));
            }
            return slides;
        }
    }

    private List<PageText> extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<PageText> paragraphs = new ArrayList<>();
            int index = 0;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (!paragraph.getText().isBlank()) paragraphs.add(new PageText("paragraph " + (++index), paragraph.getText()));
            }
            for (XWPFTable table : document.getTables()) {
                for (var row : table.getRows()) {
                    String text = row.getTableCells().stream().map(cell -> cell.getText().trim())
                            .filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.joining(" | "));
                    if (!text.isBlank()) paragraphs.add(new PageText("table paragraph " + (++index), text));
                }
            }
            return paragraphs;
        }
    }
}
