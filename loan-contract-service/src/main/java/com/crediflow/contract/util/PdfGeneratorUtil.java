package com.crediflow.contract.util;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@Component
public class PdfGeneratorUtil {

    private static final String STORAGE_PATH = "/tmp/crediflow/contracts/";

    public String generateAndStoreContractPdf(String contractNo, Long userId, String amount, String interestRate) {
        File dir = new File(STORAGE_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = contractNo + ".pdf";
        String filePath = STORAGE_PATH + fileName;

        try {
            // 1. 读取外部合同模板，内容不再写死在代码中
            ClassPathResource resource = new ClassPathResource("templates/contract_template.txt");
            String templateContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            // 2. 替换模板变量
            String finalContent = templateContent
                    .replace("${contractNo}", contractNo)
                    .replace("${userId}", String.valueOf(userId))
                    .replace("${amount}", amount)
                    .replace("${interestRate}", interestRate);

            // 3. 生成 PDF
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();
            
            // 为了支持中文，这里本应加载中文字体（如 STSong-Light），此处为演示简化
            // TODO: 电子合同准备好以后，需要接入第三方电子签名平台（如 e签宝、法大大）进行具有法律效力的数字签名和存证
            
            String[] lines = finalContent.split("\n");
            for (String line : lines) {
                document.add(new Paragraph(line));
            }
            
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF contract", e);
        }

        return "http://localhost:8080/files/contracts/" + fileName;
    }
}
