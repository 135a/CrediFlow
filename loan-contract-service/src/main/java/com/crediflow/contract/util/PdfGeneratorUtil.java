package com.crediflow.contract.util;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;

@Component
public class PdfGeneratorUtil {

    private static final String STORAGE_PATH = "/tmp/crediflow/contracts/";

    public String generateAndStoreContractPdf(String contractNo, Long userId, String amount) {
        File dir = new File(STORAGE_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = contractNo + ".pdf";
        String filePath = STORAGE_PATH + fileName;

        try {
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();
            
            document.add(new Paragraph("Personal Loan Agreement"));
            document.add(new Paragraph("-----------------------------"));
            document.add(new Paragraph("Contract No: " + contractNo));
            document.add(new Paragraph("User ID: " + userId));
            document.add(new Paragraph("Loan Amount: " + amount));
            document.add(new Paragraph("User agrees to all terms and conditions. (Checked)"));
            
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF contract", e);
        }

        // 返回模拟的文件访问链接
        return "http://localhost:8080/files/contracts/" + fileName;
    }
}
