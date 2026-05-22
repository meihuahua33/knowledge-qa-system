package com.kqa.service;

import com.kqa.model.DocumentParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 文档解析服务: 将 PDF / DOCX / TXT 解析为统一的纯文本
 *
 * 面试点:
 * - PDFBox 是按"字形坐标"提取文本, 图片/表格内容会丢失
 * - DOCX 底层是 XML, POI 提取的是 XML 里的文本节点
 * - 为什么不做 OCR? 因为 PDFBox 处理的是文字型 PDF, 扫描件需要 Tesseract
 */
@Slf4j
@Service
public class DocumentParserService {

    public DocumentParseResult parse(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileType(originalFilename);

        return switch (fileType) {
            case "pdf"  -> parsePdf(file);
            case "docx" -> parseDocx(file);
            case "txt"  -> parseTxt(file);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    // ==================== PDF 解析 ====================

    private DocumentParseResult parsePdf(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 保持段落顺序: 读取顺序 = 文档阅读顺序
            stripper.setSortByPosition(true);
            String content = stripper.getText(document);

            int pageCount = document.getNumberOfPages();
            int paraCount = content.split("\\n\\s*\\n").length; // 空行分隔段落

            return buildResult(file, content, pageCount, paraCount, "pdf");
        } catch (Exception e) {
            log.error("PDF 解析失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("PDF 解析失败", e);
        }
    }

    // ==================== DOCX 解析 ====================

    private DocumentParseResult parseDocx(MultipartFile file) {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream());
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String content = extractor.getText();
            // DOCX 没有明确的"页数"(取决于渲染引擎), 这里估为 1
            int paraCount = content.split("\\n\\s*\\n").length;

            return buildResult(file, content, 1, paraCount, "docx");
        } catch (Exception e) {
            log.error("DOCX 解析失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("DOCX 解析失败", e);
        }
    }

    // ==================== TXT 解析 ====================

    private DocumentParseResult parseTxt(MultipartFile file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            log.error("TXT 解析失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("TXT 解析失败", e);
        }

        String content = sb.toString();
        int paraCount = content.split("\\n\\s*\\n").length;

        return buildResult(file, content, 1, paraCount, "txt");
    }

    // ==================== 工具方法 ====================

    private DocumentParseResult buildResult(MultipartFile file, String content,
                                             int pages, int paraCount, String fileType) {
        String title = getTitle(file.getOriginalFilename());
        return DocumentParseResult.builder()
                .title(title)
                .content(content.trim())
                .totalPages(pages)
                .paragraphCount(paraCount)
                .charCount(content.length())
                .fileType(fileType)
                .build();
    }

    private String getTitle(String filename) {
        if (filename == null) return "未命名文档";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String getFileType(String filename) {
        if (filename == null) return "txt";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".txt"))  return "txt";
        return lower.substring(lower.lastIndexOf('.') + 1);
    }
}
