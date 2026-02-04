<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import axios from 'axios';
import { Chart, registerables } from 'chart.js';
import { API_BASE_URL } from '../config/api.js';

Chart.register(...registerables);

const sqlChartCanvas = ref(null);
const sqlChart = ref(null);
const connectionChartCanvas = ref(null);
const connectionChart = ref(null);

const statusData = ref({});
const connectionStatus = ref({});
const sqlStats = ref({});
const hotspotSqls = ref([]);
const slowQueries = ref([]);
const statistics = ref({});
const loading = ref(false);
const autoRefresh = ref(true);
let refreshInterval = null;

const fetchStatus = async () => {
  try {
    const token = localStorage.getItem('token');
    const response = await axios.get(`${API_BASE_URL}/api/mysql-monitor/status/latest`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    statusData.value = response.data;
  } catch (err) {
    console.error('获取状态数据失败:', err);
  }
};

const fetchConnectionStatus = async () => {
  try {
    const token = localStorage.getItem('token');
    const response = await axios.get(`${API_BASE_URL}/api/mysql-monitor/connections`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    connectionStatus.value = response.data;
  } catch (err) {
    console.error('获取连接状态失败:', err);
  }
};

const fetchSqlStats = async () => {
  try {
    const token = localStorage.getItem('token');
    const response = await axios.get(`${API_BASE_URL}/api/mysql-monitor/sql-stats`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    sqlStats.value = response.data;
  } catch (err) {
    console.error('获取SQL统计失败:', err);
  }
};

const fetchHotspotSqls = async () => {
  try {
    const token = localStorage.getItem('token');
    const response = await axios.get(`${API_BASE_URL}/api/mysql-monitor/hotspot-sql`, {
      params: { limit: 10 },
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    hotspotSqls.value = response.data;
  } catch (err) {
    console.error('获取热点SQL失败:', err);
  }
};

const fetchSlowQueries = async () => {
  try {
    const token = localStorage.getItem('token');
    const response = await axios.get(`${API_BASE_URL}/api/mysql-monitor/slow-queries`, {
      params: { limit: 10 },
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    slowQueries.value = response.data;
  } catch (err) {
    console.error('获取慢查询失败:', err);
  }
};

const fetchStatistics = async () => {
  try {
    const token = localStorage.getItem('token');
    const response = await axios.get(`${API_BASE_URL}/api/mysql-monitor/statistics`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    statistics.value = response.data;
  } catch (err) {
    console.error('获取统计数据失败:', err);
  }
};

const refreshAll = async () => {
  loading.value = true;
  try {
    await Promise.all([
      fetchStatus(),
      fetchConnectionStatus(),
      fetchSqlStats(),
      fetchHotspotSqls(),
      fetchSlowQueries(),
      fetchStatistics()
    ]);
  } finally {
    loading.value = false;
  }
};

const triggerManualCollect = async () => {
  try {
    const token = localStorage.getItem('token');
    await axios.post(`${API_BASE_URL}/api/mysql-monitor/collect/manual`, {}, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    refreshAll();
  } catch (err) {
    console.error('手动收集失败:', err);
  }
};

const toggleAutoRefresh = () => {
  autoRefresh.value = !autoRefresh.value;
  if (autoRefresh.value) {
    startAutoRefresh();
  } else {
    stopAutoRefresh();
  }
};

const startAutoRefresh = () => {
  refreshInterval = setInterval(refreshAll, 30000);
};

const stopAutoRefresh = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval);
    refreshInterval = null;
  }
};

const renderSqlChart = () => {
  if (sqlChartCanvas.value && sqlStats.value.command_statistics) {
    if (sqlChart.value) sqlChart.value.destroy();
    
    const commands = Object.keys(sqlStats.value.command_statistics);
    const counts = Object.values(sqlStats.value.command_statistics);
    
    const ctx = sqlChartCanvas.value.getContext('2d');
    sqlChart.value = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: commands,
        datasets: [{
          data: counts,
          backgroundColor: [
            '#4a6cf7', '#3c63f5', '#2d56f4', '#1e49f3',
            '#0f3cf2', '#002ff1', '#20c997', '#17a2b8',
            '#ffc107', '#fd7e14', '#dc3545', '#6f42c1'
          ],
          borderWidth: 0,
          hoverOffset: 8
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'right',
            labels: {
              font: {
                size: 12
              },
              padding: 16
            }
          },
          title: {
            display: true,
            text: 'SQL命令分布',
            font: {
              size: 16,
              weight: 'bold'
            },
            padding: {
              bottom: 24
            }
          }
        }
      }
    });
  }
};

const renderConnectionChart = () => {
  if (connectionChartCanvas.value && connectionStatus.value) {
    if (connectionChart.value) connectionChart.value.destroy();
    
    const ctx = connectionChartCanvas.value.getContext('2d');
    connectionChart.value = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: ['当前连接', '运行中线程', '已创建线程', '最大连接'],
        datasets: [{
          label: '数量',
          data: [
            connectionStatus.value.Threads_connected || 0,
            connectionStatus.value.Threads_running || 0,
            connectionStatus.value.Threads_created || 0,
            connectionStatus.value.Max_used_connections || 0
          ],
          backgroundColor: [
            'rgba(74, 108, 247, 0.8)',
            'rgba(32, 201, 151, 0.8)',
            'rgba(255, 193, 7, 0.8)',
            'rgba(220, 53, 69, 0.8)'
          ],
          borderColor: [
            'rgba(74, 108, 247, 1)',
            'rgba(32, 201, 151, 1)',
            'rgba(255, 193, 7, 1)',
            'rgba(220, 53, 69, 1)'
          ],
          borderWidth: 2,
          borderRadius: 8,
          barThickness: 40
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: false
          },
          title: {
            display: true,
            text: '连接状态统计',
            font: {
              size: 16,
              weight: 'bold'
            },
            padding: {
              bottom: 24
            }
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
        }
      }
    });
  }
};

watch(sqlStats, () => {
  renderSqlChart();
}, { deep: true });

watch(connectionStatus, () => {
  renderConnectionChart();
}, { deep: true });

onMounted(() => {
  refreshAll();
  if (autoRefresh.value) {
    startAutoRefresh();
  }
});

onUnmounted(() => {
  stopAutoRefresh();
  if (sqlChart.value) sqlChart.value.destroy();
  if (connectionChart.value) connectionChart.value.destroy();
});
</script>

<template>
  <div class="monitor-container">
    <div class="header">
      <h1>数据库监控中心</h1>
      <div class="header-actions">
        <button @click="toggleAutoRefresh" :class="['btn', autoRefresh ? 'btn-success' : 'btn-warning']">
          {{ autoRefresh ? '自动刷新：开启' : '自动刷新：关闭' }}
        </button>
        <button @click="refreshAll" class="btn btn-primary" :disabled="loading">
          {{ loading ? '刷新中...' : '立即刷新' }}
        </button>
        <button @click="triggerManualCollect" class="btn btn-secondary">
          手动收集
        </button>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon">📊</div>
        <div class="stat-info">
          <div class="stat-label">状态数据</div>
          <div class="stat-value">{{ statistics.status_data_count || 0 }}</div>
          <div class="stat-change">
            <span class="change-indicator">📈</span>
            <span class="change-text">实时更新</span>
          </div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">🔥</div>
        <div class="stat-info">
          <div class="stat-label">热点SQL</div>
          <div class="stat-value">{{ statistics.hotspot_sql_count || 0 }}</div>
          <div class="stat-change">
            <span class="change-indicator">⚠️</span>
            <span class="change-text">需关注</span>
          </div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">⏱️</div>
        <div class="stat-info">
          <div class="stat-label">慢查询</div>
          <div class="stat-value">{{ statistics.slow_query_count || 0 }}</div>
          <div class="stat-change">
            <span class="change-indicator">🚨</span>
            <span class="change-text">严重</span>
          </div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">📈</div>
        <div class="stat-info">
          <div class="stat-label">总查询数</div>
          <div class="stat-value">{{ sqlStats.total_queries || 0 }}</div>
          <div class="stat-change">
            <span class="change-indicator">📊</span>
            <span class="change-text">趋势</span>
          </div>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <div class="panel">
        <div class="panel-header">
          <h2>连接状态</h2>
        </div>
        <div class="panel-body">
          <div class="metric-row">
            <span class="metric-label">当前连接数</span>
            <span class="metric-value">{{ connectionStatus.Threads_connected || 0 }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">运行中线程</span>
            <span class="metric-value">{{ connectionStatus.Threads_running || 0 }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">已创建线程</span>
            <span class="metric-value">{{ connectionStatus.Threads_created || 0 }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">最大连接数</span>
            <span class="metric-value">{{ connectionStatus.Max_used_connections || 0 }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">连接使用率</span>
            <span class="metric-value highlight">{{ connectionStatus.connection_usage_rate || 'N/A' }}</span>
          </div>
          <div class="chart-container">
            <canvas ref="connectionChartCanvas"></canvas>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-header">
          <h2>SQL执行统计</h2>
        </div>
        <div class="panel-body">
          <div class="metric-row">
            <span class="metric-label">总查询数</span>
            <span class="metric-value">{{ sqlStats.total_queries || 0 }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">慢查询数</span>
            <span class="metric-value warning">{{ sqlStats.slow_query_count || 0 }}</span>
          </div>
          <div class="chart-container">
            <canvas ref="sqlChartCanvas"></canvas>
          </div>
          <div v-if="sqlStats.command_statistics" class="command-stats">
            <h3>命令统计</h3>
            <div v-for="(count, command) in sqlStats.command_statistics" :key="command" class="command-item">
              <span class="command-name">{{ command }}</span>
              <span class="command-count">{{ count }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <div class="panel full-width">
        <div class="panel-header">
          <h2>热点SQL (TOP 10)</h2>
        </div>
        <div class="panel-body">
          <div v-if="hotspotSqls.length === 0" class="empty-state">
            暂无热点SQL数据
          </div>
          <div v-else class="sql-list">
            <div v-for="(sql, index) in hotspotSqls" :key="index" class="sql-item">
              <div class="sql-rank">{{ index + 1 }}</div>
              <div class="sql-content">{{ sql.sql }}</div>
              <div class="sql-count">{{ sql.execution_count }}次</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="panel full-width">
      <div class="panel-header">
        <h2>慢查询列表</h2>
      </div>
      <div class="panel-body">
        <div v-if="slowQueries.length === 0" class="empty-state">
          暂无慢查询数据
        </div>
        <div v-else class="query-list">
          <div v-for="(query, index) in slowQueries" :key="index" class="query-item">
            <div class="query-header">
              <span class="query-time">{{ query.start_time }}</span>
              <span class="query-duration">{{ query.query_time }}s</span>
            </div>
            <div class="query-sql">{{ query.sql_text }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.monitor-container {
  padding: 32px;
  background: linear-gradient(135deg, #f8f9fc 0%, #ffffff 100%);
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding: 24px 32px;
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border: 1px solid #e3e7ed;
}

.header h1 {
  margin: 0;
  color: #2c3e50;
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 12px;
}

.header h1::before {
  content: '📊';
  font-size: 28px;
}

.header-actions {
  display: flex;
  gap: 16px;
}

.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btn-primary {
  background: linear-gradient(135deg, #4a6cf7, #3c63f5);
  color: white;
  box-shadow: 0 4px 12px rgba(74, 108, 247, 0.3);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #3c63f5, #2d56f4);
  box-shadow: 0 6px 20px rgba(74, 108, 247, 0.4);
}

.btn-secondary {
  background: linear-gradient(135deg, #6c757d, #5a6268);
  color: white;
  box-shadow: 0 2px 8px rgba(108, 117, 125, 0.3);
}

.btn-secondary:hover {
  background: linear-gradient(135deg, #5a6268, #4e555b);
  box-shadow: 0 4px 12px rgba(108, 117, 125, 0.4);
}

.btn-success {
  background: linear-gradient(135deg, #20c997, #17a2b8);
  color: white;
  box-shadow: 0 2px 8px rgba(32, 201, 151, 0.3);
}

.btn-success:hover {
  background: linear-gradient(135deg, #17a2b8, #138496);
  box-shadow: 0 4px 12px rgba(32, 201, 151, 0.4);
}

.btn-warning {
  background: linear-gradient(135deg, #ffc107, #fd7e14);
  color: #212529;
  box-shadow: 0 2px 8px rgba(255, 193, 7, 0.3);
}

.btn-warning:hover {
  background: linear-gradient(135deg, #fd7e14, #e0a800);
  box-shadow: 0 4px 12px rgba(255, 193, 7, 0.4);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.stat-card {
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
  padding: 32px;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  gap: 20px;
  border: 1px solid #e3e7ed;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(74, 108, 247, 0.15);
  border-color: #4a6cf7;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(135deg, #4a6cf7, #3c63f5);
  border-radius: 16px 0 0 16px;
}

.stat-icon {
  font-size: 48px;
  transition: transform 0.3s ease;
}

.stat-card:hover .stat-icon {
  transform: scale(1.1);
}

.stat-info {
  flex: 1;
}

.stat-label {
  color: #6c757d;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 8px;
  line-height: 1.2;
}

.stat-change {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
}

.change-indicator {
  font-size: 14px;
}

.change-text {
  color: #6c757d;
  background: #f8f9fc;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid #e3e7ed;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(480px, 1fr));
  gap: 24px;
  margin-bottom: 24px;
}

.panel {
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  border: 1px solid #e3e7ed;
  transition: all 0.3s ease;
}

.panel:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.panel.full-width {
  grid-column: 1 / -1;
}

.panel-header {
  padding: 24px 32px;
  border-bottom: 1px solid #e3e7ed;
  background: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header h2 {
  margin: 0;
  color: #2c3e50;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-body {
  padding: 32px;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s ease;
}

.metric-row:hover {
  background: #f8f9fc;
  padding-left: 16px;
  border-radius: 8px;
}

.metric-row:last-child {
  border-bottom: none;
}

.metric-label {
  color: #6c757d;
  font-size: 14px;
  font-weight: 500;
}

.metric-value {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  background: white;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid #e3e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.metric-value.highlight {
  color: #4a6cf7;
  background: #f0f2ff;
  border-color: #4a6cf7;
  box-shadow: 0 2px 8px rgba(74, 108, 247, 0.2);
}

.metric-value.warning {
  color: #dc3545;
  background: #fff8f8;
  border-color: #dc3545;
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.2);
}

.chart-container {
  margin-top: 32px;
  height: 300px;
  padding: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  border: 1px solid #e3e7ed;
}

.command-stats {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e3e7ed;
}

.command-stats h3 {
  margin: 0 0 16px 0;
  color: #2c3e50;
  font-size: 16px;
  font-weight: 600;
}

.command-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  font-size: 14px;
  transition: all 0.3s ease;
  border-radius: 8px;
  padding: 8px 12px;
}

.command-item:hover {
  background: #f8f9fc;
  transform: translateX(4px);
}

.command-name {
  color: #6c757d;
  font-weight: 500;
}

.command-count {
  font-weight: 600;
  color: #4a6cf7;
  background: #f0f2ff;
  padding: 4px 12px;
  border-radius: 12px;
  border: 1px solid #e3e7ed;
}

.empty-state {
  text-align: center;
  padding: 60px 32px;
  color: #6c757d;
  background: #f8f9fc;
  border-radius: 12px;
  border: 2px dashed #e3e7ed;
  font-size: 16px;
  font-weight: 500;
}

.empty-state::before {
  content: '📋';
  display: block;
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.sql-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sql-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
  border-radius: 12px;
  border: 1px solid #e3e7ed;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.sql-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: #4a6cf7;
}

.sql-rank {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4a6cf7, #3c63f5);
  color: white;
  border-radius: 50%;
  font-weight: 600;
  font-size: 16px;
  box-shadow: 0 2px 8px rgba(74, 108, 247, 0.3);
}

.sql-content {
  flex: 1;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  color: #2c3e50;
  word-break: break-all;
  line-height: 1.5;
  background: #f8f9fc;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid #e3e7ed;
}

.sql-count {
  padding: 8px 16px;
  background: linear-gradient(135deg, #20c997, #17a2b8);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(32, 201, 151, 0.3);
  white-space: nowrap;
}

.query-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.query-item {
  padding: 24px;
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
  border-radius: 12px;
  border-left: 4px solid #dc3545;
  border: 1px solid #e3e7ed;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  position: relative;
}

.query-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(220, 53, 69, 0.15);
  border-left-width: 8px;
}

.query-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(135deg, #dc3545, #c82333);
  border-radius: 12px 0 0 12px;
}

.query-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.query-time {
  color: #6c757d;
  font-size: 14px;
  font-weight: 500;
  background: #f8f9fc;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid #e3e7ed;
}

.query-duration {
  padding: 8px 16px;
  background: linear-gradient(135deg, #dc3545, #c82333);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.3);
}

.query-sql {
  font-family: 'Courier New', monospace;
  font-size: 14px;
  color: #2c3e50;
  word-break: break-all;
  line-height: 1.6;
  background: #f8f9fc;
  padding: 20px;
  border-radius: 8px;
  border: 1px solid #e3e7ed;
  min-height: 120px;
}

.chart-container {
  margin-top: 24px;
  height: 300px;
  position: relative;
}

/* 动画效果 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.stat-card, .panel, .sql-item, .query-item {
  animation: fadeIn 0.5s ease-out forwards;
}

.stat-card:nth-child(1) { animation-delay: 0.1s; }
.stat-card:nth-child(2) { animation-delay: 0.2s; }
.stat-card:nth-child(3) { animation-delay: 0.3s; }
.stat-card:nth-child(4) { animation-delay: 0.4s; }

.panel:nth-child(1) { animation-delay: 0.2s; }
.panel:nth-child(2) { animation-delay: 0.3s; }

/* 响应式设计 */
@media (max-width: 1200px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
  
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .monitor-container {
    padding: 16px;
  }
  
  .header {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }
  
  .header-actions {
    justify-content: center;
  }
  
  .stats-grid {
    grid-template-columns: 1fr;
  }
  
  .panel-body {
    padding: 20px;
  }
  
  .metric-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
