<script setup>
import { ref, onMounted, watch } from 'vue';
import axios from 'axios';
import { API_BASE_URL } from '../config/api.js';

// 订单数据 - 初始化为空数组
const orders = ref([]);
// 加载状态
const loading = ref(false);
// 模态框状态
const showAddModal = ref(false);
const showEditModal = ref(false);
// 当前编辑的订单
const currentOrder = ref(null);
// 分页信息
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
// 搜索和筛选
const searchKeyword = ref('');
const filterStatus = ref('');
const filterSize = ref('10');
// 新增订单表单数据
const newOrder = ref({
  waybillNo: '',
  sourceOrderNo: '',
  loadingDistrict: '',
  loadingAddress: '',
  unloadingDistrict: '',
  unloadingAddress: '',
  loadingWeight: '',
  unloadingWeight: '',
  transportPlateNo: '',
  cargoMainType: '',
  cargoSubType: '',
  loadingTime: '',
  unloadingTime: '',
  loadingWeightBillUrls: '',
  unloadingWeightBillUrls: ''
});

// 获取订单列表
const fetchOrders = async () => {
  loading.value = true;
  try {
    console.log('开始获取订单列表...');
    const token = localStorage.getItem('token');
    
    const response = await axios.get(`${API_BASE_URL}/logistics/pagelist`, {
      params: {
        pageNum: currentPage.value,
        pageSize: pageSize.value
      },
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    console.log('API响应:', response.data);
    
    // 检查响应数据
    if (response.data && response.data.success === true) {
      // 后端返回success: true格式
      orders.value = response.data.data?.list || response.data.data?.items || [];
      total.value = response.data.data?.total || 0;
      console.log('获取订单列表成功:', orders.value.length, '条记录');
    } else if (response.data && response.data.code === 0) {
      // 后端返回code: 0格式
      orders.value = response.data.data?.list || response.data.data?.items || [];
      total.value = response.data.data?.total || 0;
      console.log('获取订单列表成功:', orders.value.length, '条记录');
    } else {
      // API返回错误
      console.error('获取订单列表失败:', response.data?.message || '未知错误');
      orders.value = [];
      total.value = 0;
    }
  } catch (err) {
    // 网络错误
    console.error('获取订单列表失败（网络错误）:', err);
    orders.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
    console.log('获取订单列表操作完成，当前订单数:', orders.value.length);
  }
};

// 处理新增订单
const handleAdd = () => {
  // 重置表单
  newOrder.value = {
    waybillNo: '',
    sourceOrderNo: '',
    loadingDistrict: '',
    loadingAddress: '',
    unloadingDistrict: '',
    unloadingAddress: '',
    loadingWeight: '',
    unloadingWeight: '',
    transportPlateNo: '',
    cargoMainType: '',
    cargoSubType: '',
    loadingTime: '',
    unloadingTime: '',
    loadingWeightBillUrls: '',
    unloadingWeightBillUrls: ''
  };
  showAddModal.value = true;
};

// 提交新增订单
const submitAdd = async () => {
  try {
    const token = localStorage.getItem('token');
    const response = await axios.post(`${API_BASE_URL}/logistics/add`, newOrder.value, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    if (response.data && (response.data.success === true || response.data.code === 0)) {
      showAddModal.value = false;
      fetchOrders();
    } else {
      console.error('添加订单失败:', response.data?.message || '未知错误');
    }
  } catch (err) {
    console.error('添加订单失败:', err);
  }
};

// 处理编辑订单
const handleEdit = (order) => {
  if (order) {
    currentOrder.value = { ...order };
    showEditModal.value = true;
  }
};

// 提交编辑订单
const submitEdit = async () => {
  if (!currentOrder.value) return;
  
  try {
    const token = localStorage.getItem('token');
    const response = await axios.put(`${API_BASE_URL}/logistics`, currentOrder.value, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    if (response.data && (response.data.success === true || response.data.code === 0)) {
      showEditModal.value = false;
      fetchOrders();
    } else {
      console.error('更新订单失败:', response.data?.message || '未知错误');
    }
  } catch (err) {
    console.error('更新订单失败:', err);
  }
};

// 处理删除订单
const handleDelete = async (id) => {
  if (!id || !confirm('确定要删除该订单吗？')) return;
  
  try {
    const token = localStorage.getItem('token');
    const response = await axios.delete(`${API_BASE_URL}/logistics/delete/${id}`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    if (response.data && (response.data.success === true || response.data.code === 0)) {
      fetchOrders();
    } else {
      console.error('删除订单失败:', response.data?.message || '未知错误');
    }
  } catch (err) {
    console.error('删除订单失败:', err);
  }
};

// 处理搜索
const handleSearch = () => {
  currentPage.value = 1;
  fetchOrders();
};

// 处理分页变化
const handlePageChange = (page) => {
  if (page >= 1) {
    currentPage.value = page;
    fetchOrders();
  }
};

// 监听分页大小变化
watch(filterSize, (newSize) => {
  pageSize.value = parseInt(newSize);
  currentPage.value = 1;
  fetchOrders();
});

// 监听筛选状态变化
watch(filterStatus, () => {
  currentPage.value = 1;
  fetchOrders();
});

// 页面加载时获取订单列表
onMounted(() => {
  console.log('LogisticsManagement组件挂载，开始获取订单列表');
  fetchOrders();
});
</script>

<template>
  <div class="logistics-container">
    <div class="header">
      <h1>智慧物流管理系统</h1>
      <button @click="handleAdd" class="btn btn-primary btn-lg">
        <span class="icon">+</span> 新增订单
      </button>
    </div>

    <!-- 搜索和筛选 -->
    <div class="search-filter">
      <div class="search-box">
        <input 
          v-model="searchKeyword" 
          type="text" 
          placeholder="搜索运单号、来源货单、车牌号..."
          class="search-input"
        >
        <button @click="handleSearch" class="btn btn-search">
          <span class="icon">🔍</span> 搜索
        </button>
      </div>
      <div class="filter-box">
        <select v-model="filterStatus" class="filter-select">
          <option value="">全部状态</option>
          <option value="pending">待发货</option>
          <option value="transit">运输中</option>
          <option value="delivered">已送达</option>
        </select>
        <select v-model="filterSize" class="filter-select">
          <option value="10">10条/页</option>
          <option value="20">20条/页</option>
          <option value="50">50条/页</option>
        </select>
      </div>
    </div>

    <div class="table-container">
      <div class="table-wrapper">
        <table class="order-table">
          <thead>
            <tr>
              <th>运单号</th>
              <th>来源货单</th>
              <th>装货地</th>
              <th>卸货地</th>
              <th>货物信息</th>
              <th class="plate-column">运输车牌</th>
              <th>装货时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <!-- 加载状态 -->
            <tr v-if="loading">
              <td colspan="8" class="loading">
                加载中...
              </td>
            </tr>
            <!-- 无数据状态 -->
            <tr v-else-if="orders && orders.length === 0">
              <td colspan="8" class="empty">
                暂无订单数据
              </td>
            </tr>
            <!-- 订单列表 -->
            <tr v-else v-for="order in orders" :key="order.id">
              <td class="order-no">{{ order.waybillNo || '' }}</td>
              <td class="truncate" :title="order.sourceOrderNo || ''">{{ order.sourceOrderNo || '' }}</td>
              <td class="truncate loading-address" :title="order.loadingAddress || ''">{{ order.loadingAddress || '' }}</td>
              <td class="truncate unloading-address" :title="order.unloadingAddress || ''">{{ order.unloadingAddress || '' }}</td>
              <td class="truncate goods-info-column">
                <div class="goods-info">
                  <div class="name truncate" :title="(order.cargoMainType || '') + ' / ' + (order.cargoSubType || '')">
                    {{ order.cargoMainType || '' }} / {{ order.cargoSubType || '' }}
                  </div>
                  <div class="details truncate" :title="(order.loadingWeight || '') + 'kg / ' + (order.unloadingWeight || '') + 'kg'">
                    {{ order.loadingWeight || '' }}kg / {{ order.unloadingWeight || '' }}kg
                  </div>
                </div>
              </td>
              <td class="truncate plate-column" :title="order.transportPlateNo || ''">{{ order.transportPlateNo || '' }}</td>
              <td class="truncate" :title="order.loadingTime || ''">{{ order.loadingTime || '' }}</td>
              <td>
                <div class="action-buttons">
                  <button @click="handleEdit(order)" class="btn btn-sm btn-edit">编辑</button>
                  <button @click="handleDelete(order.id)" class="btn btn-sm btn-delete">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 分页控件 -->
    <div class="pagination">
      <button 
        @click="handlePageChange(currentPage - 1)" 
        :disabled="currentPage === 1"
        class="btn btn-page"
      >
        上一页
      </button>
      <span class="page-info">第 {{ currentPage }} 页</span>
      <button 
        @click="handlePageChange(currentPage + 1)" 
        :disabled="currentPage * pageSize >= total"
        class="btn btn-page"
      >
        下一页
      </button>
      <span class="total-info">共 {{ total }} 条记录</span>
    </div>

    <!-- 新增订单模态框 -->
    <div v-if="showAddModal" class="modal-overlay">
      <div class="modal">
        <div class="modal-header">
          <h2>新增订单</h2>
          <button @click="showAddModal = false" class="close-btn">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="form-group">
              <label>运单号</label>
              <input v-model="newOrder.waybillNo" type="text" placeholder="请输入运单号">
            </div>
            <div class="form-group">
              <label>来源货单</label>
              <input v-model="newOrder.sourceOrderNo" type="text" placeholder="请输入来源货单">
            </div>
            <div class="form-group">
              <label>装货地县区</label>
              <input v-model="newOrder.loadingDistrict" type="text" placeholder="请输入装货地县区">
            </div>
            <div class="form-group">
              <label>装货地址</label>
              <input v-model="newOrder.loadingAddress" type="text" placeholder="请输入装货地址">
            </div>
            <div class="form-group">
              <label>卸货地县区</label>
              <input v-model="newOrder.unloadingDistrict" type="text" placeholder="请输入卸货地县区">
            </div>
            <div class="form-group">
              <label>卸货地址</label>
              <input v-model="newOrder.unloadingAddress" type="text" placeholder="请输入卸货地址">
            </div>
            <div class="form-group">
              <label>装货重量</label>
              <input v-model="newOrder.loadingWeight" type="text" placeholder="请输入装货重量">
            </div>
            <div class="form-group">
              <label>卸货重量</label>
              <input v-model="newOrder.unloadingWeight" type="text" placeholder="请输入卸货重量">
            </div>
            <div class="form-group">
              <label>运输车牌号</label>
              <input v-model="newOrder.transportPlateNo" type="text" placeholder="请输入运输车牌号">
            </div>
            <div class="form-group">
              <label>货物大类型</label>
              <input v-model="newOrder.cargoMainType" type="text" placeholder="请输入货物大类型">
            </div>
            <div class="form-group">
              <label>货物小类型</label>
              <input v-model="newOrder.cargoSubType" type="text" placeholder="请输入货物小类型">
            </div>
            <div class="form-group">
              <label>装货时间</label>
              <input v-model="newOrder.loadingTime" type="text" placeholder="请输入装货时间">
            </div>
            <div class="form-group">
              <label>卸货时间</label>
              <input v-model="newOrder.unloadingTime" type="text" placeholder="请输入卸货时间">
            </div>
            <div class="form-group">
              <label>装货磅单地址</label>
              <input v-model="newOrder.loadingWeightBillUrls" type="text" placeholder="请输入装货磅单地址">
            </div>
            <div class="form-group">
              <label>卸货磅单地址</label>
              <input v-model="newOrder.unloadingWeightBillUrls" type="text" placeholder="请输入卸货磅单地址">
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button @click="showAddModal = false" class="btn btn-secondary">取消</button>
          <button @click="submitAdd" class="btn btn-primary">确定</button>
        </div>
      </div>
    </div>

    <!-- 编辑订单模态框 -->
    <div v-if="showEditModal" class="modal-overlay">
      <div class="modal">
        <div class="modal-header">
          <h2>编辑订单</h2>
          <button @click="showEditModal = false" class="close-btn">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="form-group">
              <label>运单号</label>
              <input v-model="currentOrder.waybillNo" type="text" placeholder="请输入运单号">
            </div>
            <div class="form-group">
              <label>来源货单</label>
              <input v-model="currentOrder.sourceOrderNo" type="text" placeholder="请输入来源货单">
            </div>
            <div class="form-group">
              <label>装货地县区</label>
              <input v-model="currentOrder.loadingDistrict" type="text" placeholder="请输入装货地县区">
            </div>
            <div class="form-group">
              <label>装货地址</label>
              <input v-model="currentOrder.loadingAddress" type="text" placeholder="请输入装货地址">
            </div>
            <div class="form-group">
              <label>卸货地县区</label>
              <input v-model="currentOrder.unloadingDistrict" type="text" placeholder="请输入卸货地县区">
            </div>
            <div class="form-group">
              <label>卸货地址</label>
              <input v-model="currentOrder.unloadingAddress" type="text" placeholder="请输入卸货地址">
            </div>
            <div class="form-group">
              <label>装货重量</label>
              <input v-model="currentOrder.loadingWeight" type="text" placeholder="请输入装货重量">
            </div>
            <div class="form-group">
              <label>卸货重量</label>
              <input v-model="currentOrder.unloadingWeight" type="text" placeholder="请输入卸货重量">
            </div>
            <div class="form-group">
              <label>运输车牌号</label>
              <input v-model="currentOrder.transportPlateNo" type="text" placeholder="请输入运输车牌号">
            </div>
            <div class="form-group">
              <label>货物大类型</label>
              <input v-model="currentOrder.cargoMainType" type="text" placeholder="请输入货物大类型">
            </div>
            <div class="form-group">
              <label>货物小类型</label>
              <input v-model="currentOrder.cargoSubType" type="text" placeholder="请输入货物小类型">
            </div>
            <div class="form-group">
              <label>装货时间</label>
              <input v-model="currentOrder.loadingTime" type="text" placeholder="请输入装货时间">
            </div>
            <div class="form-group">
              <label>卸货时间</label>
              <input v-model="currentOrder.unloadingTime" type="text" placeholder="请输入卸货时间">
            </div>
            <div class="form-group">
              <label>装货磅单地址</label>
              <input v-model="currentOrder.loadingWeightBillUrls" type="text" placeholder="请输入装货磅单地址">
            </div>
            <div class="form-group">
              <label>卸货磅单地址</label>
              <input v-model="currentOrder.unloadingWeightBillUrls" type="text" placeholder="请输入卸货磅单地址">
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button @click="showEditModal = false" class="btn btn-secondary">取消</button>
          <button @click="submitEdit" class="btn btn-primary">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.logistics-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 24px 32px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.header h1 {
  margin: 0;
  color: #2c3e50;
  font-size: 24px;
  font-weight: 600;
}

.btn-lg {
  padding: 12px 24px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(74, 108, 247, 0.3);
  transition: all 0.3s ease;
}

.btn-lg:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(74, 108, 247, 0.4);
}

/* 搜索和筛选 */
.search-filter {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 20px 32px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  gap: 20px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  max-width: 600px;
}

.search-input {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid #f0f2f5;
  border-radius: 12px;
  font-size: 14px;
  transition: all 0.3s ease;
  background: #f8f9fc;
}

.search-input:focus {
  outline: none;
  border-color: #4a6cf7;
  background: white;
  box-shadow: 0 0 0 3px rgba(74, 108, 247, 0.1);
}

.btn-search {
  padding: 12px 20px;
  background: #4a6cf7;
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-search:hover {
  background: #3c63f5;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(74, 108, 247, 0.3);
}

.filter-box {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-select {
  padding: 10px 16px;
  border: 2px solid #f0f2f5;
  border-radius: 12px;
  font-size: 14px;
  background: #f8f9fc;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 120px;
}

.filter-select:focus {
  outline: none;
  border-color: #4a6cf7;
  background: white;
  box-shadow: 0 0 0 3px rgba(74, 108, 247, 0.1);
}

.table-container {
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  margin-bottom: 24px;
  overflow: hidden;
}

.table-wrapper {
  width: 100%;
  overflow-x: auto;
}

.order-table {
  width: 100%;
  min-width: 1000px;
  border-collapse: collapse;
  font-size: 13px;
  line-height: 1.4;
}

.order-table thead {
  background: linear-gradient(135deg, #f8f9fc 0%, #e9ecef 100%);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  position: sticky;
  top: 0;
  z-index: 10;
}

.order-table th {
  padding: 18px 24px;
  text-align: left;
  font-weight: 600;
  color: #2c3e50;
  font-size: 14px;
  letter-spacing: 0.5px;
  border-bottom: 3px solid #4a6cf7;
  position: relative;
  white-space: nowrap;
}

.order-table th::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 24px;
  right: 24px;
  height: 3px;
  background: linear-gradient(90deg, #4a6cf7, #3c63f5);
  border-radius: 3px;
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.order-table th:hover::after {
  transform: scaleX(1);
}

.order-table td {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f2f5;
  font-size: 13px;
  color: #495057;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.order-table tbody tr {
  transition: all 0.3s ease;
  border-left: 3px solid transparent;
}

.order-table tbody tr:hover {
  background-color: #f8f9fc;
  transform: translateX(4px);
  border-left-color: #4a6cf7;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.order-table tbody tr:hover td {
  color: #2c3e50;
}

/* 突出显示运单号 */
.order-no {
  font-weight: 600;
  color: #4a6cf7 !important;
  font-size: 14px !important;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

/* 文本截断样式 */
.truncate {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

/* 列宽设置 */
.loading-address {
  max-width: 200px;
}

.unloading-address {
  max-width: 200px;
}

.goods-info-column {
  max-width: 250px;
  white-space: normal;
}

/* 货物信息样式优化 */
.goods-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  white-space: normal;
}

.goods-info .name {
  font-weight: 600;
  color: #2c3e50;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.goods-info .details {
  font-size: 12px;
  color: #6c757d;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.loading,
.empty {
  text-align: center;
  padding: 40px;
  color: #6c757d;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .loading-address,
  .unloading-address {
    max-width: 150px;
  }
  
  .goods-info-column {
    max-width: 200px;
  }
}

@media (max-width: 768px) {
  .table-wrapper {
    overflow-x: auto;
  }

  .order-table {
    min-width: 800px;
  }

  .plate-column {
    display: none;
  }

  .loading-address,
  .unloading-address {
    max-width: 120px;
  }

  .goods-info-column {
    max-width: 150px;
  }
}

@media (max-width: 480px) {
  .order-table {
    min-width: 600px;
  }

  .loading-address,
  .unloading-address {
    max-width: 100px;
  }

  .goods-info-column {
    max-width: 120px;
  }
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.btn {
  padding: 10px 18px;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
  transition: left 0.5s ease;
}

.btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.btn:hover::before {
  left: 100%;
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

.btn-primary:hover {
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

.btn-sm {
  padding: 8px 16px;
  font-size: 13px;
  border-radius: 10px;
}

.btn-edit {
  background: linear-gradient(135deg, #20c997, #17a2b8);
  color: white;
  box-shadow: 0 2px 8px rgba(32, 201, 151, 0.3);
}

.btn-edit:hover {
  background: linear-gradient(135deg, #17a2b8, #138496);
  box-shadow: 0 4px 12px rgba(32, 201, 151, 0.4);
}

.btn-delete {
  background: linear-gradient(135deg, #dc3545, #c82333);
  color: white;
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.3);
}

.btn-delete:hover {
  background: linear-gradient(135deg, #c82333, #a71e2a);
  box-shadow: 0 4px 12px rgba(220, 53, 69, 0.4);
}

.btn-page {
  padding: 10px 18px;
  border: 2px solid #f0f2f5;
  background: white;
  color: #495057;
  border-radius: 12px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.btn-page:hover:not(:disabled) {
  background: #f8f9fc;
  border-color: #4a6cf7;
  color: #4a6cf7;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(74, 108, 247, 0.2);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 24px;
  padding: 24px 32px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.page-info {
  font-size: 14px;
  color: #6c757d;
  font-weight: 500;
  padding: 0 20px;
}

.total-info {
  font-size: 14px;
  color: #4a6cf7;
  font-weight: 600;
  margin-left: 24px;
  padding: 10px 16px;
  background: #f8f9fc;
  border-radius: 12px;
  border: 1px solid #e3e7ed;
}

/* 加载和空状态优化 */
.loading, .empty {
  text-align: center;
  padding: 60px 20px;
  color: #6c757d;
  font-size: 16px;
  font-weight: 500;
}

.loading {
  background: linear-gradient(135deg, #f8f9fc 0%, #e9ecef 100%);
  border-radius: 12px;
  margin: 20px;
}

.empty {
  background: linear-gradient(135deg, #f8f9fc 0%, #f0f2f5 100%);
  border-radius: 12px;
  margin: 20px;
  border: 2px dashed #dee2e6;
}

.empty::before {
  content: '📦';
  display: block;
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 800px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e9ecef;
}

.modal-header h2 {
  margin: 0;
  color: #2c3e50;
  font-size: 20px;
}

.close-btn {
  background: none;
  border: none;
  font-size: 28px;
  cursor: pointer;
  color: #6c757d;
  line-height: 1;
}

.close-btn:hover {
  color: #2c3e50;
}

.modal-body {
  padding: 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group label {
  margin-bottom: 8px;
  font-weight: 500;
  color: #2c3e50;
  font-size: 14px;
}

.form-group input,
.form-group select {
  padding: 10px;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 14px;
  transition: border-color 0.3s;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.1);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 20px;
  border-top: 1px solid #e9ecef;
}

.icon {
  font-size: 18px;
  font-weight: bold;
}
</style>