package com.checkai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.PageBean;
import com.checkai.mapper.LogisticsMapper;
import com.checkai.util.RedisUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class LoginsticsService extends ServiceImpl<LogisticsMapper, LogisticsOrder> {

    private static final String USER_CACHE_PREFIX = "LOGISTICS:";
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();
    private static final String[] DATE_TIME_PATTERNS = new String[] {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/M/d H:mm:ss",
            "yyyy/M/d H:mm",
            "yyyy-MM-dd'T'HH:mm:ss"
    };

    private final LogisticsMapper logisticsMapper;

    @Autowired
    private RedisUtil redisUtil;

    public LoginsticsService(LogisticsMapper logisticsMapper) {
        this.logisticsMapper = logisticsMapper;
    }

    private String userPrefix(String userId) {
        return USER_CACHE_PREFIX + userId + ":";
    }

    private void keyClear(String userId) {
        Set<String> keys = redisUtil.getS(userPrefix(userId) + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            redisUtil.delete(key);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(MultipartFile file, String userId) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择 Excel 文件");
        }

        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 文件中没有可读取的工作表");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel 文件缺少表头");
            }

            Map<Integer, String> fieldMapping = buildFieldMapping(headerRow);
            if (fieldMapping.isEmpty()) {
                throw new IllegalArgumentException("未识别到可导入的表头字段");
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row, fieldMapping)) {
                    continue;
                }

                LogisticsOrder order = new LogisticsOrder();
                order.setUserId(userId);

                for (Map.Entry<Integer, String> entry : fieldMapping.entrySet()) {
                    applyCellValue(order, entry.getValue(), row.getCell(entry.getKey()));
                }

                if (order.getWaybillNo() == null || order.getWaybillNo().trim().isEmpty()) {
                    skippedCount++;
                    continue;
                }

                LogisticsOrder existing = logisticsMapper.selectOne(
                        new LambdaQueryWrapper<LogisticsOrder>()
                                .eq(LogisticsOrder::getUserId, userId)
                                .eq(LogisticsOrder::getWaybillNo, order.getWaybillNo())
                                .last("limit 1")
                );

                if (existing == null) {
                    logisticsMapper.insert(order);
                    insertedCount++;
                } else {
                    order.setId(existing.getId());
                    UpdateWrapper<LogisticsOrder> wrapper = new UpdateWrapper<LogisticsOrder>()
                            .eq("id", existing.getId())
                            .eq("user_id", userId);
                    logisticsMapper.update(order, wrapper);
                    updatedCount++;
                }
            }
        }

        keyClear(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("insertedCount", insertedCount);
        result.put("updatedCount", updatedCount);
        result.put("skippedCount", skippedCount);
        result.put("totalCount", insertedCount + updatedCount);
        return result;
    }

    private Map<Integer, String> buildFieldMapping(Row headerRow) {
        Map<Integer, String> fieldMapping = new LinkedHashMap<>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            String header = getCellString(headerRow.getCell(cellIndex));
            if (header == null || header.trim().isEmpty()) {
                continue;
            }
            String fieldName = mapHeaderToField(header.trim());
            if (fieldName != null) {
                fieldMapping.put(cellIndex, fieldName);
            }
        }
        return fieldMapping;
    }

    private String mapHeaderToField(String header) {
        return switch (header) {
            case "运单号" -> "waybillNo";
            case "来源货单" -> "sourceOrderNo";
            case "装货地县区" -> "loadingDistrict";
            case "装货地址" -> "loadingAddress";
            case "卸货地县区" -> "unloadingDistrict";
            case "卸货地址" -> "unloadingAddress";
            case "装货重量" -> "loadingWeight";
            case "卸货重量" -> "unloadingWeight";
            case "运输车牌号" -> "transportPlateNo";
            case "货物大类型" -> "cargoMainType";
            case "货物小类型" -> "cargoSubType";
            case "装货时间" -> "loadingTime";
            case "卸货时间" -> "unloadingTime";
            case "装货磅单地址" -> "loadingWeightBillUrls";
            case "卸货货磅单地址", "卸货磅单地址" -> "unloadingWeightBillUrls";
            default -> null;
        };
    }

    private boolean isEmptyRow(Row row, Map<Integer, String> fieldMapping) {
        for (Integer cellIndex : fieldMapping.keySet()) {
            String value = getCellString(row.getCell(cellIndex));
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void applyCellValue(LogisticsOrder order, String fieldName, Cell cell) {
        switch (fieldName) {
            case "waybillNo" -> order.setWaybillNo(getCellString(cell));
            case "sourceOrderNo" -> order.setSourceOrderNo(getCellString(cell));
            case "loadingDistrict" -> order.setLoadingDistrict(getCellString(cell));
            case "loadingAddress" -> order.setLoadingAddress(getCellString(cell));
            case "unloadingDistrict" -> order.setUnloadingDistrict(getCellString(cell));
            case "unloadingAddress" -> order.setUnloadingAddress(getCellString(cell));
            case "loadingWeight" -> order.setLoadingWeight(parseBigDecimal(cell));
            case "unloadingWeight" -> order.setUnloadingWeight(parseBigDecimal(cell));
            case "transportPlateNo" -> order.setTransportPlateNo(getCellString(cell));
            case "cargoMainType" -> order.setCargoMainType(getCellString(cell));
            case "cargoSubType" -> order.setCargoSubType(getCellString(cell));
            case "loadingTime" -> order.setLoadingTime(parseLocalDateTime(cell));
            case "unloadingTime" -> order.setUnloadingTime(parseLocalDateTime(cell));
            case "loadingWeightBillUrls" -> order.setLoadingWeightBillUrls(getCellString(cell));
            case "unloadingWeightBillUrls" -> order.setUnloadingWeightBillUrls(getCellString(cell));
            default -> {
            }
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = DATA_FORMATTER.formatCellValue(cell);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private BigDecimal parseBigDecimal(Cell cell) {
        String value = getCellString(cell);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        String value = getCellString(cell);
        if (value == null) {
            return null;
        }

        for (String pattern : DATE_TIME_PATTERNS) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return LocalDateTime.of(date, LocalTime.MIN);
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy/M/d"));
            return LocalDateTime.of(date, LocalTime.MIN);
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    public PageBean<LogisticsOrder> LogisticsList(Integer pageNum, Integer pageSize, String userId) {
        Page<LogisticsOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogisticsOrder::getUserId, userId)
                .orderByDesc(LogisticsOrder::getId);
        Page<LogisticsOrder> logisticsOrderPage = logisticsMapper.selectPage(page, queryWrapper);
        PageBean<LogisticsOrder> pageBean = new PageBean<>();
        pageBean.setTotal(logisticsOrderPage.getTotal());
        pageBean.setItems(logisticsOrderPage.getRecords());
        return pageBean;
    }

    public PageBean<LogisticsOrder> LogisticsListWithSearch(Integer pageNum, Integer pageSize, String keyword, String userId) {
        String safeKeyword = keyword != null ? keyword.trim() : "";
        String cacheKey = userPrefix(userId) + "PAGE:" + pageNum + ":" + pageSize + ":" + safeKeyword;
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return (PageBean<LogisticsOrder>) cached;
        }

        Page<LogisticsOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogisticsOrder::getUserId, userId);

        if (!safeKeyword.isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like(LogisticsOrder::getWaybillNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getSourceOrderNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getTransportPlateNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getLoadingAddress, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getUnloadingAddress, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getCargoMainType, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getCargoSubType, safeKeyword)
            );
        }

        queryWrapper.orderByDesc(LogisticsOrder::getId);
        Page<LogisticsOrder> logisticsOrderPage = logisticsMapper.selectPage(page, queryWrapper);
        PageBean<LogisticsOrder> pageBean = new PageBean<>();
        pageBean.setTotal(logisticsOrderPage.getTotal());
        pageBean.setItems(logisticsOrderPage.getRecords());

        redisUtil.set(cacheKey, pageBean, 5, TimeUnit.MINUTES);
        return pageBean;
    }

    public List<LogisticsOrder> logisticsSelectWithSearch(String keyword, String userId) {
        String safeKeyword = keyword != null ? keyword.trim() : "";
        String cacheKey = userPrefix(userId) + "SEARCH:" + safeKeyword;
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return (List<LogisticsOrder>) cached;
        }

        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogisticsOrder::getUserId, userId);

        if (!safeKeyword.isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like(LogisticsOrder::getWaybillNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getSourceOrderNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getTransportPlateNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getLoadingAddress, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getUnloadingAddress, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getCargoMainType, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getCargoSubType, safeKeyword)
            );
        }

        queryWrapper.orderByDesc(LogisticsOrder::getId);
        List<LogisticsOrder> result = logisticsMapper.selectList(queryWrapper);
        redisUtil.set(cacheKey, result, 5, TimeUnit.MINUTES);
        return result;
    }

    public List<LogisticsOrder> logisticsSelect(String userId) {
        String key = userPrefix(userId) + "LIST";
        Object cached = redisUtil.get(key);
        if (cached != null) {
            return (List<LogisticsOrder>) cached;
        }

        QueryWrapper<LogisticsOrder> queryWrapper = new QueryWrapper<LogisticsOrder>()
                .eq("user_id", userId)
                .orderByDesc("id");
        List<LogisticsOrder> logisticsOrders = logisticsMapper.selectList(queryWrapper);
        redisUtil.set(key, logisticsOrders, 30, TimeUnit.MINUTES);
        return logisticsOrders;
    }

    public void logisticsAdd(LogisticsOrder logisticsOrder, String userId) {
        logisticsOrder.setUserId(userId);
        logisticsMapper.insert(logisticsOrder);
        keyClear(userId);
    }

    public boolean logisticsUpdate(LogisticsOrder logisticsOrder, String userId) {
        logisticsOrder.setUserId(userId);
        UpdateWrapper<LogisticsOrder> wrapper = new UpdateWrapper<LogisticsOrder>()
                .eq("id", logisticsOrder.getId())
                .eq("user_id", userId);
        int updated = logisticsMapper.update(logisticsOrder, wrapper);
        if (updated > 0) {
            keyClear(userId);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2000);
                    keyClear(userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return true;
        }
        return false;
    }

    public boolean logisticsDelete(Long id, String userId) {
        LambdaQueryWrapper<LogisticsOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsOrder::getId, id)
                .eq(LogisticsOrder::getUserId, userId);
        int deleted = logisticsMapper.delete(wrapper);
        if (deleted > 0) {
            keyClear(userId);
            return true;
        }
        return false;
    }

    public LogisticsOrder logisticsSelectById(Long id, String userId) {
        String key = userPrefix(userId) + "SelectById:" + id;
        Object value = redisUtil.get(key);
        if (value != null) {
            return (LogisticsOrder) value;
        }

        LambdaQueryWrapper<LogisticsOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsOrder::getId, id)
                .eq(LogisticsOrder::getUserId, userId);
        LogisticsOrder logisticsOrder = logisticsMapper.selectOne(wrapper);
        if (logisticsOrder != null) {
            redisUtil.set(key, logisticsOrder, 30, TimeUnit.MINUTES);
        }
        return logisticsOrder;
    }

    public LogisticsOrder logisticsSelectByWaybillNo(String waybillNo, String userId) {
        if (waybillNo == null || waybillNo.trim().isEmpty()) {
            return null;
        }

        String normalizedWaybillNo = waybillNo.trim();
        String key = userPrefix(userId) + "SelectByWaybill:" + normalizedWaybillNo;
        Object value = redisUtil.get(key);
        if (value != null) {
            return (LogisticsOrder) value;
        }

        LambdaQueryWrapper<LogisticsOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsOrder::getUserId, userId)
                .eq(LogisticsOrder::getWaybillNo, normalizedWaybillNo)
                .last("limit 1");
        LogisticsOrder logisticsOrder = logisticsMapper.selectOne(wrapper);
        if (logisticsOrder != null) {
            redisUtil.set(key, logisticsOrder, 30, TimeUnit.MINUTES);
        }
        return logisticsOrder;
    }
}
