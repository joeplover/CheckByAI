package com.checkai.service;

import com.checkai.dto.ExcelData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExcelService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s,]+");

    public ExcelData parseExcel(InputStream inputStream) throws Exception {
        ExcelData excelData = new ExcelData();
        List<Map<String, String>> excelBase = new ArrayList<>();
        List<List<String>> excelPull = new ArrayList<>();
        List<List<String>> excelPush = new ArrayList<>();

        // 使用WorkbookFactory自动处理.xls和.xlsx格式
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new Exception("Excel sheet has no header");
            }

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue().trim());
            }

            // 确定URL列
            List<String> pullCols = new ArrayList<>();
            List<String> pushCols = new ArrayList<>();

            for (String header : headers) {
                if (header.contains("装货") && header.contains("地址")) {
                    pullCols.add(header);
                }
                if (header.contains("卸货") && header.contains("地址")) {
                    pushCols.add(header);
                }
            }

            // 如果没找到，使用默认列名
            if (pullCols.isEmpty()) {
                if (headers.contains("装货地址")) {
                    pullCols.add("装货地址");
                }
            }
            if (pushCols.isEmpty()) {
                if (headers.contains("卸货地址")) {
                    pushCols.add("卸货地址");
                }
            }

            // 处理数据行
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row dataRow = sheet.getRow(rowIndex);
                if (dataRow == null) continue;

                Map<String, String> baseData = new HashMap<>();
                List<String> rowPullUrls = new ArrayList<>();
                List<String> rowPushUrls = new ArrayList<>();

                for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                    String header = headers.get(colIndex);
                    Cell cell = dataRow.getCell(colIndex);
                    String cellValue = getCellValue(cell);

                    if (pullCols.contains(header) && !cellValue.isEmpty()) {
                        rowPullUrls.addAll(extractUrls(cellValue));
                    } else if (pushCols.contains(header) && !cellValue.isEmpty()) {
                        rowPushUrls.addAll(extractUrls(cellValue));
                    } else if (!pullCols.contains(header) && !pushCols.contains(header) && !cellValue.isEmpty()) {
                        baseData.put(header, cellValue);
                    }
                }

                excelBase.add(baseData);
                excelPull.add(rowPullUrls);
                excelPush.add(rowPushUrls);
            }
        }

        excelData.setExcelBase(excelBase);
        excelData.setExcelPull(excelPull);
        excelData.setExcelPush(excelPush);

        return excelData;
    }

    private List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(text);

        while (matcher.find()) {
            String url = matcher.group().trim();
            // 去除末尾的标点符号
            url = url.replaceAll("[.,;]$", "");
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(url);
            }
        }

        return urls;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    public List<ExcelData> splitExcelData(ExcelData excelData, int batchSize) {
        List<ExcelData> splitDataList = new ArrayList<>();
        List<Map<String, String>> excelBase = excelData.getExcelBase();
        List<List<String>> excelPull = excelData.getExcelPull();
        List<List<String>> excelPush = excelData.getExcelPush();

        int totalRows = excelBase.size();
        for (int i = 0; i < totalRows; i += batchSize) {
            ExcelData splitData = new ExcelData();
            int endIndex = Math.min(i + batchSize, totalRows);
            
            splitData.setExcelBase(excelBase.subList(i, endIndex));
            splitData.setExcelPull(excelPull.subList(i, endIndex));
            splitData.setExcelPush(excelPush.subList(i, endIndex));
            
            splitDataList.add(splitData);
        }

        return splitDataList;
    }
}
