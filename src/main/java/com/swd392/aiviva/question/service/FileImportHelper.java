package com.swd392.aiviva.question.service;

import com.swd392.aiviva.common.exception.BusinessException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FileImportHelper {

    public static class QuestionImportDto {
        public String questionText;
        public String bloomLevel;
    }

    public static List<QuestionImportDto> parseFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException("File name is invalid");
        }
        String lowerCaseName = filename.toLowerCase();

        try {
            if (lowerCaseName.endsWith(".csv")) {
                return parseCsv(file);
            } else if (lowerCaseName.endsWith(".xlsx")) {
                return parseExcel(file);
            } else {
                throw new BusinessException("Unsupported file format. Please upload .csv or .xlsx file");
            }
        } catch (Exception e) {
            throw new BusinessException("Failed to parse file: " + e.getMessage());
        }
    }

    private static List<QuestionImportDto> parseCsv(MultipartFile file) throws Exception {
        List<QuestionImportDto> list = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            for (CSVRecord csvRecord : csvParser) {
                QuestionImportDto dto = new QuestionImportDto();
                // Assumes columns "Question Text" and optionally "Bloom Level"
                // Fallback to by index if header missing
                if (csvRecord.isMapped("Question Text")) {
                    dto.questionText = csvRecord.get("Question Text");
                } else if (csvRecord.size() > 0) {
                    dto.questionText = csvRecord.get(0);
                }
                
                if (csvRecord.isMapped("Bloom Level")) {
                    dto.bloomLevel = csvRecord.get("Bloom Level");
                } else if (csvRecord.size() > 1) {
                    dto.bloomLevel = csvRecord.get(1);
                }
                
                if (dto.questionText != null && !dto.questionText.isBlank()) {
                    list.add(dto);
                }
            }
        }
        return list;
    }

    private static List<QuestionImportDto> parseExcel(MultipartFile file) throws Exception {
        List<QuestionImportDto> list = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            int rowNumber = 0;
            while (rows.hasNext()) {
                Row currentRow = rows.next();

                // Skip header
                if (rowNumber == 0) {
                    rowNumber++;
                    continue;
                }

                QuestionImportDto dto = new QuestionImportDto();
                Cell textCell = currentRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (textCell != null) {
                    if (textCell.getCellType() == CellType.STRING) {
                        dto.questionText = textCell.getStringCellValue();
                    } else if (textCell.getCellType() == CellType.NUMERIC) {
                        dto.questionText = String.valueOf(textCell.getNumericCellValue());
                    }
                }
                
                Cell bloomCell = currentRow.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (bloomCell != null && bloomCell.getCellType() == CellType.STRING) {
                    dto.bloomLevel = bloomCell.getStringCellValue();
                }

                if (dto.questionText != null && !dto.questionText.isBlank()) {
                    list.add(dto);
                }
                rowNumber++;
            }
        }
        return list;
    }
}
