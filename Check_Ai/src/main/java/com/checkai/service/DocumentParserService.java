package com.checkai.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.checkai.config.AiConfigProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentParserService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentParserService.class);
    
    @Autowired
    private AiConfigProperties aiConfig;
    
    private final Tika tika = new Tika();
    
    public List<TextSegment> parseDocument(MultipartFile file, String userId) throws Exception {
        String filename = file.getOriginalFilename();
        String extension = filename != null && filename.contains(".") 
            ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase() 
            : "";
        
        logger.info("解析文档: filename={}, extension={}, userId={}", filename, extension, userId);
        
        String content;
        switch (extension) {
            case "txt":
                content = parseTextFile(file.getInputStream());
                break;
            case "pdf":
                content = parsePdfFile(file.getInputStream());
                break;
            case "doc":
            case "docx":
            case "md":
            case "html":
            case "htm":
                content = parseWithTika(file.getInputStream());
                break;
            default:
                content = parseWithTika(file.getInputStream());
        }
        
        if (content == null || content.trim().isEmpty()) {
            throw new Exception("文档内容为空");
        }
        
        return splitContent(content, filename, userId);
    }
    
    public List<TextSegment> parseDocument(File file, String userId) throws Exception {
        String filename = file.getName();
        String extension = filename.contains(".") 
            ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase() 
            : "";
        
        logger.info("解析文档: filename={}, extension={}, userId={}", filename, extension, userId);
        
        String content;
        switch (extension) {
            case "txt":
                content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                break;
            case "pdf":
                content = parsePdfFile(Files.newInputStream(file.toPath()));
                break;
            default:
                content = parseWithTika(Files.newInputStream(file.toPath()));
        }
        
        if (content == null || content.trim().isEmpty()) {
            throw new Exception("文档内容为空");
        }
        
        return splitContent(content, filename, userId);
    }
    
    private String parseTextFile(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
    
    private String parsePdfFile(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
    
    private String parseWithTika(InputStream inputStream) throws Exception {
        return tika.parseToString(inputStream);
    }
    
    private List<TextSegment> splitContent(String content, String filename, String userId) {
        DocumentSplitter splitter = DocumentSplitters.recursive(
            aiConfig.getRag().getMaxSegmentSize(),
            aiConfig.getRag().getMaxOverlapSize()
        );
        
        Metadata metadata = Metadata.from(
            Map.of(
                "filename", filename != null ? filename : "unknown",
                "userId", userId,
                "documentId", UUID.randomUUID().toString()
            )
        );
        
        Document document = Document.from(content, metadata);
        List<TextSegment> segments = splitter.split(document);
        
        List<TextSegment> segmentsWithMetadata = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            Metadata segmentMetadata = new Metadata();
            segmentMetadata.put("userId", userId);
            segmentMetadata.put("filename", filename != null ? filename : "unknown");
            segmentMetadata.put("segmentIndex", String.valueOf(i));
            segmentMetadata.put("documentId", metadata.get("documentId"));
            
            segmentsWithMetadata.add(TextSegment.from(segment.text(), segmentMetadata));
        }
        
        logger.info("文档分割完成: segments={}, filename={}, userId={}", segmentsWithMetadata.size(), filename, userId);
        return segmentsWithMetadata;
    }
    
    public File convertMultipartFileToFile(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }
        return tempFile;
    }
}
