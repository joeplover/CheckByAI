package com.checkai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ExcelData {
    private List<Map<String, String>> excelBase;
    private List<List<String>> excelPull;
    private List<List<String>> excelPush;
}
