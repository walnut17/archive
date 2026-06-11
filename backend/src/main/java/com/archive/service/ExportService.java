package com.archive.service;

import com.archive.entity.Material;
import com.archive.entity.Project;
import com.archive.entity.Proposal;
import com.archive.repository.MaterialRepository;
import com.archive.repository.ProjectRepository;
import com.archive.repository.ProposalRepository;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.NoSuchElementException;

/**
 * 数据导出服务 (RI-64, OpenPDF + POI).
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ProjectRepository projectRepository;
    private final ProposalRepository proposalRepository;
    private final MaterialRepository materialRepository;
    private final AuditLogService auditLogService;

    public byte[] exportProjectPdf(Long projectId) throws Exception {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + projectId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, baos);
        doc.open();
        doc.add(new Paragraph("项目报告 - " + p.getCode()));
        doc.add(new Paragraph("项目名称: " + p.getName()));
        doc.add(new Paragraph("项目金额: " + (p.getAmountWan() != null ? p.getAmountWan() : 0) + " 万元"));
        doc.add(new Paragraph("项目阶段: " + p.getStatus()));
        doc.add(new Paragraph("摘要: " + (p.getSummary() != null ? p.getSummary() : "")));
        doc.close();

        auditLogService.logExport(currentActor(), "project_pdf", projectId);
        return baos.toByteArray();
    }

    public byte[] exportSingleProjectExcel(Long projectId) throws Exception {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + projectId));
        SXSSFWorkbook wb = new SXSSFWorkbook();
        Sheet sheet = wb.createSheet("project");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("code");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("region");
        header.createCell(3).setCellValue("stage");
        header.createCell(4).setCellValue("amount");
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue(p.getCode());
        row.createCell(1).setCellValue(p.getName());
        row.createCell(2).setCellValue(p.getCategory() != null ? p.getCategory() : "");
        row.createCell(3).setCellValue(p.getStatus());
        row.createCell(4).setCellValue(p.getAmountWan() != null ? p.getAmountWan() : 0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        wb.close();
        auditLogService.logExport(currentActor(), "project_xlsx", projectId);
        return baos.toByteArray();
    }

    public byte[] exportProjectsExcel(String type) throws Exception {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        Sheet sheet = wb.createSheet(type);
        Row header = sheet.createRow(0);

        switch (type) {
            case "materials" -> {
                header.createCell(0).setCellValue("code");
                header.createCell(1).setCellValue("name");
                header.createCell(2).setCellValue("type");
                header.createCell(3).setCellValue("size");
                header.createCell(4).setCellValue("createdAt");
                var materials = materialRepository.findAll(PageRequest.of(0, 1000)).getContent();
                int rowIdx = 1;
                for (Material m : materials) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(m.getId() != null ? m.getId().toString() : "");
                    row.createCell(1).setCellValue(m.getTitle());
                    row.createCell(2).setCellValue(m.getCategory() != null ? m.getCategory() : "");
                    row.createCell(3).setCellValue("");
                    row.createCell(4).setCellValue(m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
                }
            }
            case "proposals" -> {
                header.createCell(0).setCellValue("code");
                header.createCell(1).setCellValue("projectCode");
                header.createCell(2).setCellValue("title");
                header.createCell(3).setCellValue("status");
                header.createCell(4).setCellValue("meetingResult");
                var proposals = proposalRepository.findAll(PageRequest.of(0, 1000)).getContent();
                int rowIdx = 1;
                for (Proposal pr : proposals) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(pr.getCode());
                    Project proj = projectRepository.findById(pr.getProjectId()).orElse(null);
                    row.createCell(1).setCellValue(proj != null ? proj.getCode() : "");
                    row.createCell(2).setCellValue(pr.getTitle());
                    row.createCell(3).setCellValue(pr.getStatus());
                    row.createCell(4).setCellValue(pr.getDecision() != null ? pr.getDecision() : "");
                }
            }
            case "facts", "projects" -> {
                header.createCell(0).setCellValue("code");
                header.createCell(1).setCellValue("name");
                header.createCell(2).setCellValue("region");
                header.createCell(3).setCellValue("stage");
                header.createCell(4).setCellValue("amount");
                var projects = projectRepository.findAll(PageRequest.of(0, 1000)).getContent();
                int rowIdx = 1;
                for (Project p : projects) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(p.getCode());
                    row.createCell(1).setCellValue(p.getName());
                    row.createCell(2).setCellValue(p.getCategory() != null ? p.getCategory() : "");
                    row.createCell(3).setCellValue(p.getStatus());
                    row.createCell(4).setCellValue(p.getAmountWan() != null ? p.getAmountWan() : 0);
                }
            }
            default -> throw new IllegalArgumentException("不支持的导出类型: " + type);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        wb.close();
        auditLogService.logExport(currentActor(), type + "_xlsx", null);
        return baos.toByteArray();
    }

    private String currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getName();
        }
        return "system";
    }
}
