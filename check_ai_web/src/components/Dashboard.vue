<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import axios from 'axios';
import { API_BASE_URL } from '../config/api.js';
import Chart from 'chart.js/auto';

const stats = ref({
  totalOrders: 0,
  pendingOrders: 0,
  transitOrders: 0,
  deliveredOrders: 0
});

const recentOrders = ref([]);
const recentTasks = ref([]);
const loading = ref(false);

// 订单趋势数据
const orderTrend = ref({
  labels: ['1月', '2月', '3月', '4月', '5月', '6月'],
  datasets: [
    {
      label: '订单数量',
      data: [120, 190, 150, 210, 180, 240],
      borderColor: '#4a6cf7',
      backgroundColor: 'rgba(74, 108, 247, 0.1)',
      tension: 0.4,
      fill: true
    }
  ]
});

// 图表实例
const trendChart = ref(null);
const chartCanvas = ref(null);

const fetchDashboardStats = async () => {
  try {
    const token = localStorage.getItem('token');
    
    const ordersResponse = await axios.get(`${API_BASE_URL}/logistics/list`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    if (ordersResponse.data && ordersResponse.data.code === 0) {
      const orders = ordersResponse.data.data;
      stats.value.totalOrders = orders.length;
      stats.value.pendingOrders = orders.length;
      stats.value.transitOrders = 0;
      stats.value.deliveredOrders = 0;
      
      recentOrders.value = orders.slice(0, 5);
    } else {
      recentOrders.value = [];
    }
    
    const tasksResponse = await axios.get(`${API_BASE_URL}/api/tasks`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    // /api/tasks 后端返回：{ success: true, tasks: [...] }
    if (tasksResponse.data && tasksResponse.data.success === true) {
      recentTasks.value = (tasksResponse.data.tasks || []).slice(0, 5);
    } else {
      recentTasks.value = [];
    }
  } catch (err) {
    console.error('获取仪表盘数据失败:', err);
    recentOrders.value = [];
    recentTasks.value = [];
  }
};

// 渲染趋势图表
const renderTrendChart = () => {
  if (chartCanvas.value) {
    // 销毁现有图表
    if (trendChart.value) {
      trendChart.value.destroy();
    }
    
    // 创建新图表
    const ctx = chartCanvas.value.getContext('2d');
    trendChart.value = new Chart(ctx, {
      type: 'line',
      data: orderTrend.value,
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'top',
            labels: {
              usePointStyle: true,
              boxWidth: 8
            }
          },
          tooltip: {
            mode: 'index',
            intersect: false
          }
        },
        scales: {
          y: {
            beginAtZero: true,
            grid: {
              color: 'rgba(0, 0, 0, 0.05)'
            }
          },
          x: {
            grid: {
              display: false
            }
          }
        },
        interaction: {
          mode: 'nearest',
          axis: 'x',
          intersect: false
        }
      }
    });
  }
};

onMounted(() => {
  fetchDashboardStats();
  // 延迟渲染图表，确保DOM已更新
  setTimeout(renderTrendChart, 100);
});

onUnmounted(() => {
  // 销毁图表实例
  if (trendChart.value) {
    trendChart.value.destroy();
  }
});
</script>

<template>
  <div class="dashboard-container">
    <div class="header">
      <h1>智慧物流系统 - 仪表盘</h1>
      <p class="subtitle">欢迎回来，查看系统概览</p>
    </div>

    <div class="stats-grid">
      <div class="stat-card primary">
        <div class="stat-icon">📦</div>
        <div class="stat-content">
          <div class="stat-label">总订单数</div>
          <div class="stat-value">{{ stats.totalOrders }}</div>
        </div>
      </div>
      <div class="stat-card warning">
        <div class="stat-icon">⏳</div>
        <div class="stat-content">
          <div class="stat-label">待发货</div>
          <div class="stat-value">{{ stats.pendingOrders }}</div>
        </div>
      </div>
      <div class="stat-card info">
        <div class="stat-icon">🚚</div>
        <div class="stat-content">
          <div class="stat-label">运输中</div>
          <div class="stat-value">{{ stats.transitOrders }}</div>
        </div>
      </div>
      <div class="stat-card success">
        <div class="stat-icon">✅</div>
        <div class="stat-content">
          <div class="stat-label">已送达</div>
          <div class="stat-value">{{ stats.deliveredOrders }}</div>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <div class="panel">
        <div class="panel-header">
          <h2>最近订单</h2>
          <router-link to="/logistics" class="view-all">查看全部</router-link>
        </div>
        <div class="panel-body">
          <div v-if="recentOrders.length === 0" class="empty-state">
            暂无订单数据
          </div>
          <div v-else class="order-list">
            <div v-for="order in recentOrders" :key="order.id" class="order-item">
              <div class="order-info">
                <div class="order-no">{{ order.waybillNo }}</div>
                <div class="order-route">
                  {{ order.loadingAddress }} → {{ order.unloadingAddress }}
                </div>
              </div>
              <div class="order-status">
                已完成
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-header">
          <h2>最近任务</h2>
          <router-link to="/main" class="view-all">查看全部</router-link>
        </div>
        <div class="panel-body">
          <div v-if="recentTasks.length === 0" class="empty-state">
            暂无任务数据
          </div>
          <div v-else class="task-list">
            <div v-for="task in recentTasks" :key="task.id" class="task-item">
              <div class="task-info">
                <div class="task-id">{{ task.taskId.substring(0, 12) }}...</div>
                <div class="task-time">{{ new Date(task.createTime).toLocaleString() }}</div>
              </div>
              <div class="task-status">
                {{ task.status === 'COMPLETED' ? '已完成' : task.status === 'PROCESSING' ? '处理中' : task.status }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 订单趋势图表 -->
    <div class="chart-panel">
      <div class="panel-header">
        <h2>订单趋势分析</h2>
        <div class="chart-period">
          <button class="period-btn active">6个月</button>
          <button class="period-btn">3个月</button>
          <button class="period-btn">1个月</button>
        </div>
      </div>
      <div class="panel-body">
        <div class="chart-container">
          <canvas ref="chartCanvas"></canvas>
        </div>
      </div>
    </div>

    <div class="quick-actions">
      <h2>快捷操作</h2>
      <div class="actions-grid">
        <router-link to="/logistics" class="action-card">
          <div class="action-icon">📋</div>
          <div class="action-label">管理订单</div>
        </router-link>
        <router-link to="/main" class="action-card">
          <div class="action-icon">📁</div>
          <div class="action-label">上传文件</div>
        </router-link>
        <router-link to="/monitor" class="action-card">
          <div class="action-icon">📊</div>
          <div class="action-label">数据监控</div>
        </router-link>
        <router-link to="/logistics" class="action-card">
          <div class="action-icon">➕</div>
          <div class="action-label">新增订单</div>
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-container {
  padding: var(--space-6);
  background-color: var(--neutral-50);
  min-height: 100vh;
}

.header {
  margin-bottom: var(--space-7);
  padding: var(--space-7);
  background: linear-gradient(135deg, var(--primary-600) 0%, var(--primary-700) 100%);
  border-radius: var(--radius-2xl);
  color: white;
  box-shadow: var(--shadow-xl);
  position: relative;
  overflow: hidden;
  animation: fadeIn 0.8s ease-out forwards;
}

.header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  animation: float 6s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0px) rotate(0deg); }
  50% { transform: translateY(-20px) rotate(5deg); }
}

.header h1 {
  margin: 0 0 var(--space-2) 0;
  font-size: var(--text-3xl);
  font-weight: var(--font-bold);
  position: relative;
  z-index: 1;
}

.subtitle {
  margin: 0;
  opacity: 0.9;
  font-size: var(--text-base);
  position: relative;
  z-index: 1;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--space-6);
  margin-bottom: var(--space-7);
}

.stat-card {
  padding: var(--space-7);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
  display: flex;
  align-items: center;
  gap: var(--space-6);
  transition: all var(--transition-normal);
  position: relative;
  overflow: hidden;
  animation: fadeIn 0.6s ease-out forwards;
}

.stat-card:nth-child(1) { animation-delay: 0.1s; }
.stat-card:nth-child(2) { animation-delay: 0.2s; }
.stat-card:nth-child(3) { animation-delay: 0.3s; }
.stat-card:nth-child(4) { animation-delay: 0.4s; }

.stat-card:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: var(--shadow-xl);
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  opacity: 0.6;
}

.stat-card.primary::before {
  background: linear-gradient(90deg, var(--primary-500), var(--primary-600));
}

.stat-card.warning::before {
  background: linear-gradient(90deg, var(--warning-500), var(--warning-600));
}

.stat-card.info::before {
  background: linear-gradient(90deg, var(--success-600), var(--success-500));
}

.stat-card.success::before {
  background: linear-gradient(90deg, var(--success-500), var(--success-600));
}

.stat-card.primary {
  background: linear-gradient(135deg, #ffffff 0%, #f8faff 100%);
}

.stat-card.warning {
  background: linear-gradient(135deg, #ffffff 0%, #fff8f0 100%);
}

.stat-card.info {
  background: linear-gradient(135deg, #ffffff 0%, #f0f9ff 100%);
}

.stat-card.success {
  background: linear-gradient(135deg, #ffffff 0%, #f0fff4 100%);
}

.stat-icon {
  font-size: 48px;
  transition: transform var(--transition-normal);
}

.stat-card:hover .stat-icon {
  transform: scale(1.1) rotate(5deg);
}

.stat-content {
  flex: 1;
}

.stat-label {
  color: var(--neutral-600);
  font-size: var(--text-sm);
  margin-bottom: var(--space-2);
  font-weight: var(--font-medium);
}

.stat-value {
  font-size: var(--text-3xl);
  font-weight: var(--font-bold);
  color: var(--neutral-800);
  transition: transform var(--transition-fast);
}

.stat-card:hover .stat-value {
  transform: translateX(4px);
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: var(--space-5);
  margin-bottom: var(--space-6);
}

.panel {
  background: white;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  animation: fadeIn 0.7s ease-out forwards;
  transition: box-shadow var(--transition-normal);
}

.panel:hover {
  box-shadow: var(--shadow-lg);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--neutral-200);
  background-color: var(--neutral-50);
}

.panel-header h2 {
  margin: 0;
  color: var(--neutral-800);
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
}

.view-all {
  color: var(--primary-600);
  text-decoration: none;
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  transition: all var(--transition-fast);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.view-all:hover {
  color: var(--primary-700);
  background-color: var(--primary-50);
  transform: translateX(2px);
}

.panel-body {
  padding: var(--space-5) var(--space-6);
}

.empty-state {
  text-align: center;
  padding: var(--space-9) 0;
  color: var(--neutral-600);
  font-size: var(--text-sm);
  background-color: var(--neutral-50);
  border-radius: var(--radius-lg);
  margin: var(--space-2) 0;
  animation: fadeIn 0.5s ease-out forwards;
}

.order-list,
.task-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.order-item,
.task-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4);
  background-color: var(--neutral-50);
  border-radius: var(--radius-lg);
  transition: all var(--transition-normal);
  border-left: 3px solid transparent;
  cursor: pointer;
}

.order-item:hover,
.task-item:hover {
  background-color: var(--neutral-100);
  transform: translateX(4px);
  border-left-color: var(--primary-500);
  box-shadow: var(--shadow-sm);
}

.order-info,
.task-info {
  flex: 1;
}

.order-no,
.task-id {
  font-weight: var(--font-semibold);
  color: var(--neutral-800);
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
}

.order-route,
.task-time {
  color: var(--neutral-600);
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
}

.order-status,
.task-status {
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  white-space: nowrap;
  transition: all var(--transition-fast);
}

.order-item:hover .order-status,
.task-item:hover .task-status {
  transform: scale(1.05);
}

.order-status.待发货,
.task-status.待发货 {
  background-color: #fff3cd;
  color: #856404;
}

.order-status.运输中,
.task-status.运输中 {
  background-color: #cce5ff;
  color: #004085;
}

.order-status.已送达,
.task-status.已送达 {
  background-color: #d4edda;
  color: #155724;
}

.order-status.已取消,
.task-status.已取消 {
  background-color: #f8d7da;
  color: #721c24;
}

.task-status.PROCESSING,
.task-status.处理中 {
  background-color: #cce5ff;
  color: #004085;
}

.task-status.COMPLETED,
.task-status.已完成 {
  background-color: #d4edda;
  color: #155724;
}

.chart-panel {
  background: white;
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  margin-bottom: var(--space-7);
  animation: fadeIn 0.9s ease-out forwards;
}

.chart-panel:hover {
  box-shadow: var(--shadow-lg);
}

.chart-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-6) var(--space-7);
  border-bottom: 1px solid var(--neutral-200);
  background-color: var(--neutral-50);
}

.chart-panel .panel-header h2 {
  margin: 0;
  color: var(--neutral-800);
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
}

.chart-period {
  display: flex;
  gap: var(--space-2);
  background-color: white;
  padding: var(--space-1);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.period-btn {
  padding: var(--space-2) var(--space-4);
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--neutral-600);
  transition: all var(--transition-fast);
  position: relative;
  overflow: hidden;
}

.period-btn:hover {
  color: var(--primary-600);
  background-color: var(--primary-50);
}

.period-btn.active {
  background: linear-gradient(135deg, var(--primary-600) 0%, var(--primary-700) 100%);
  color: white;
  box-shadow: var(--shadow-sm);
}

.period-btn.active:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.chart-panel .panel-body {
  padding: var(--space-7);
}

.chart-container {
  height: 300px;
  position: relative;
  animation: fadeIn 1s ease-out forwards;
}

.chart-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.1) 0%, transparent 100%);
  pointer-events: none;
}

.quick-actions {
  background: white;
  padding: var(--space-7);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
  animation: fadeIn 1s ease-out forwards;
}

.quick-actions h2 {
  margin: 0 0 var(--space-6) 0;
  color: var(--neutral-800);
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
}

.actions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-5);
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-7);
  background: linear-gradient(135deg, var(--primary-600) 0%, var(--primary-700) 100%);
  border-radius: var(--radius-xl);
  color: white;
  text-decoration: none;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-lg);
  position: relative;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.action-card::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  transform: rotate(45deg);
  transition: transform 0.6s ease;
}

.action-card:hover::before {
  transform: rotate(45deg) scale(1.2);
}

.action-card:hover {
  transform: translateY(-8px) scale(1.03);
  box-shadow: var(--shadow-2xl);
  border-color: rgba(255, 255, 255, 0.2);
}

.action-card:active {
  transform: translateY(-4px) scale(1.01);
}

.action-icon {
  font-size: 40px;
  margin-bottom: var(--space-3);
  transition: transform var(--transition-normal);
  position: relative;
  z-index: 1;
}

.action-card:hover .action-icon {
  transform: scale(1.1) rotate(10deg);
}

.action-label {
  font-size: var(--text-base);
  font-weight: var(--font-medium);
  position: relative;
  z-index: 1;
  transition: transform var(--transition-fast);
}

.action-card:hover .action-label {
  transform: translateY(2px);
}

/* 加载动画 */
@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.loading {
  animation: pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: var(--space-5);
  }
  
  .stat-card {
    padding: var(--space-6);
    gap: var(--space-4);
  }
  
  .stat-value {
    font-size: var(--text-2xl);
  }
  
  .content-grid {
    grid-template-columns: 1fr;
  }
  
  .actions-grid {
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: var(--space-4);
  }
  
  .action-card {
    padding: var(--space-6);
  }
}

@media (max-width: 768px) {
  .dashboard-container {
    padding: var(--space-4);
  }
  
  .header {
    padding: var(--space-5);
  }
  
  .header h1 {
    font-size: var(--text-2xl);
  }
  
  .stats-grid {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }
  
  .stat-card {
    padding: var(--space-5);
  }
  
  .panel {
    border-radius: var(--radius-lg);
  }
  
  .panel-header,
  .panel-body {
    padding: var(--space-4);
  }
  
  .chart-period {
    flex-wrap: wrap;
  }
  
  .period-btn {
    font-size: var(--text-xs);
    padding: var(--space-1) var(--space-3);
  }
  
  .actions-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .actions-grid {
    grid-template-columns: 1fr;
  }
  
  .chart-container {
    height: 250px;
  }
}
</style>
