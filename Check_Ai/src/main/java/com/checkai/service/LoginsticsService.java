package com.checkai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.PageBean;
import com.checkai.mapper.LogisticsMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.checkai.util.RedisUtil;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
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
    @Autowired
    private static final String USER_CACHE_PREFIX = "LOGISTICS:";

    /**
     * 分页查询物流订单列表
     * 按照订单ID降序排列，返回指定页码和页面大小的数据
     *
     * @param pageNum  当前页码，从1开始
     * @param pageSize 每页显示的记录数
     * @return PageBean<LogisticsOrder> 包含总记录数和当前页数据的分页对象
     */
    public PageBean<LogisticsOrder> LogisticsList(Integer pageNum, Integer pageSize) {
        // 创建分页对象，指定当前页和每页大小
        Page<LogisticsOrder> page = new Page<>(pageNum, pageSize);

        // 创建查询条件构造器
        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        // 设置按照ID降序排列
        queryWrapper.orderByDesc(LogisticsOrder::getId);

        // 执行分页查询
        Page<LogisticsOrder> logisticsOrderPage = logisticsMapper.selectPage(page, queryWrapper);

        // 封装分页结果到自定义的PageBean对象
        PageBean<LogisticsOrder> pageBean = new PageBean<>();
        // 设置总记录数
        pageBean.setTotal(logisticsOrderPage.getTotal());
        // 设置当前页的数据列表
        pageBean.setItems(logisticsOrderPage.getRecords());

        return pageBean;
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
}

