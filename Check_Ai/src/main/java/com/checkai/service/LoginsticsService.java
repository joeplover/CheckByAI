package com.checkai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.PageBean;
import com.checkai.mapper.LogisticsMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.checkai.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service

/**
 * 物流订单服务类
 * 继承MyBatis-Plus的ServiceImpl，提供物流订单的业务逻辑处理
 */
public class LoginsticsService extends ServiceImpl<LogisticsMapper, LogisticsOrder> {
    /**
     * 物流订单数据访问层对象
     */
    LogisticsMapper logisticsMapper;

    /**
     * 构造函数，通过依赖注入初始化LogisticsMapper
     *
     * @param logisticsMapper 物流订单数据访问层对象
     */
    public LoginsticsService(LogisticsMapper logisticsMapper) {
        this.logisticsMapper = logisticsMapper;
    }

    @Autowired
    private RedisUtil redisUtil;
    
    private static final String USER_CACHE_PREFIX = "LOGISTICS:";

    private void keyClear() {
        for (String s : redisUtil.getS(USER_CACHE_PREFIX + "*")) {
            redisUtil.delete(s);
        }
    }

    /**
     * 分页查询物流订单列表
     * 按照订单ID降序排列，返回指定页码和页面大小的数据
     *
     * @param pageNum  当前页码，从1开始
     * @param pageSize 每页显示的记录数
     * @return PageBean<LogisticsOrder> 包含总记录数和当前页数据的分页对象
     */
    public PageBean<LogisticsOrder> LogisticsList(Integer pageNum, Integer pageSize) {
        Page<LogisticsOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(LogisticsOrder::getId);
        Page<LogisticsOrder> logisticsOrderPage = logisticsMapper.selectPage(page, queryWrapper);
        PageBean<LogisticsOrder> pageBean = new PageBean<>();
        pageBean.setTotal(logisticsOrderPage.getTotal());
        pageBean.setItems(logisticsOrderPage.getRecords());
        return pageBean;
    }

    public PageBean<LogisticsOrder> LogisticsListWithSearch(Integer pageNum, Integer pageSize, String keyword) {
        String cacheKey = USER_CACHE_PREFIX + "PAGE:" + pageNum + ":" + pageSize + ":" + (keyword != null ? keyword.trim() : "");
        
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return (PageBean<LogisticsOrder>) cached;
        }
        
        Page<LogisticsOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                .like(LogisticsOrder::getWaybillNo, searchKeyword)
                .or()
                .like(LogisticsOrder::getSourceOrderNo, searchKeyword)
                .or()
                .like(LogisticsOrder::getTransportPlateNo, searchKeyword)
                .or()
                .like(LogisticsOrder::getLoadingAddress, searchKeyword)
                .or()
                .like(LogisticsOrder::getUnloadingAddress, searchKeyword)
                .or()
                .like(LogisticsOrder::getCargoMainType, searchKeyword)
                .or()
                .like(LogisticsOrder::getCargoSubType, searchKeyword)
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

    public List<LogisticsOrder> logisticsSelectWithSearch(String keyword) {
        String cacheKey = USER_CACHE_PREFIX + "SEARCH:" + (keyword != null ? keyword.trim() : "");
        
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return (List<LogisticsOrder>) cached;
        }
        
        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                .like(LogisticsOrder::getWaybillNo, searchKeyword)
                .or()
                .like(LogisticsOrder::getSourceOrderNo, searchKeyword)
                .or()
                .like(LogisticsOrder::getTransportPlateNo, searchKeyword)
                .or()
                .like(LogisticsOrder::getLoadingAddress, searchKeyword)
                .or()
                .like(LogisticsOrder::getUnloadingAddress, searchKeyword)
                .or()
                .like(LogisticsOrder::getCargoMainType, searchKeyword)
                .or()
                .like(LogisticsOrder::getCargoSubType, searchKeyword)
            );
        }
        
        queryWrapper.orderByDesc(LogisticsOrder::getId);
        List<LogisticsOrder> result = logisticsMapper.selectList(queryWrapper);
        
        redisUtil.set(cacheKey, result, 5, TimeUnit.MINUTES);
        return result;
    }

    public List<LogisticsOrder> logisticsSelect() {
        String key = USER_CACHE_PREFIX + "LIST";

        List<LogisticsOrder> cacheLogisticsOrders = (List<LogisticsOrder>) redisUtil.get(key);
        if (cacheLogisticsOrders !=null){
            return cacheLogisticsOrders;
        }
        System.out.println("缓存未命中，从数据库查询数据");
        QueryWrapper<LogisticsOrder> queryWrapper = new QueryWrapper<LogisticsOrder>()
                .select("*");
        List<LogisticsOrder> logisticsOrders = logisticsMapper.selectList(queryWrapper);
        if (logisticsOrders == null){
            System.out.println("数据库为空");
            return null;
        }
        redisUtil.set(key, logisticsOrders,30, TimeUnit.MINUTES);
        System.out.println("数据已缓存到Redis,30分钟内有效");
        return logisticsOrders;
    }

    public void logisticsAdd(LogisticsOrder logisticsOrder) {
        logisticsMapper.insert(logisticsOrder);
        keyClear();
    }

    public void logisticsUpdate(LogisticsOrder logisticsOrder) {

        keyClear();

        UpdateWrapper<LogisticsOrder> wrapper = new UpdateWrapper<LogisticsOrder>()
                .eq("id", logisticsOrder.getId());
        logisticsMapper.update(logisticsOrder,wrapper);

        //异步删除 为数据库update预留充足的时间
        CompletableFuture.runAsync(()->{
            try {
                Thread.sleep(2000);
                keyClear();
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
        }, Executors.newSingleThreadExecutor());

    }

    public void logisticsDelete(Long id) {
        logisticsMapper.deleteById(id);
        keyClear();
    }

    public LogisticsOrder logisticsSelectById(Long id) {
        String key = USER_CACHE_PREFIX + "SelectById:" + id;
        Object value = redisUtil.get(key);
        if (!redisUtil.hasKey(key)) {
            LogisticsOrder logisticsOrder = logisticsMapper.selectById(id);
            redisUtil.set(key, logisticsOrder,30, TimeUnit.MINUTES);
            return logisticsOrder;
        }else {
            return (LogisticsOrder) value;
        }

    }
}

