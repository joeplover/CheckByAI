<script setup>
import { computed, onMounted, ref } from 'vue';
import axios from 'axios';
import { API_BASE_URL, API_PATHS, buildApiUrl } from '../config/api';

const loading = ref(false);
const saving = ref(false);
const reviewStatusFilter = ref('');
const reviewItems = ref([]);
const selectedItem = ref(null);
const detailVisible = ref(false);
const taskResultLoading = ref(false);
const taskResultText = ref('');

const reviewForm = ref({
  reviewStatus: 'UNREVIEWED',
  riskLevel: '',
  tags: '',
  remark: '',
  reviewResult: ''
});

const reviewStatusOptions = [
  { label: '全部', value: '' },
  { label: '未复核', value: 'UNREVIEWED' },
  { label: '复核中', value: 'REVIEWING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' }
];

const riskLevelOptions = [
  { label: '未设置', value: '' },
  { label: '低风险', value: 'LOW' },
  { label: '中风险', value: 'MEDIUM' },
  { label: '高风险', value: 'HIGH' },
  { label: '严重', value: 'CRITICAL' }
];

const reviewStatusLabelMap = {
  UNREVIEWED: '未复核',
  REVIEWING: '复核中',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
  CLOSED: '已关闭'
};

const taskStatusLabelMap = {
  PENDING: '待处理',
  SENT: '已发送',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
};

const totalCount = computed(() => reviewItems.value.length);

const getAuthHeaders = () => {
  const token = localStorage.getItem('token');
  return {
    Authorization: token ? `Bearer ${token}` : ''
  };
};

const decodeUnicode = (str) => {
  if (!str) return '';
  try {
    if (/^[A-Za-z0-9+/=]+$/.test(str) && str.length % 4 === 0) {
      str = decodeURIComponent(escape(atob(str)));
    }
  } catch (error) {
    // ignore
  }

  return str.replace(/\\u([0-9a-fA-F]{4})/g, (_, code) =>
    String.fromCharCode(parseInt(code, 16))
  );
};

const formatDate = (dateString) => {
  if (!dateString) return '-';
  return new Date(dateString).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};

const loadReviewItems = async () => {
  loading.value = true;
  try {
    const params = {};
    if (reviewStatusFilter.value) {
      params.reviewStatus = reviewStatusFilter.value;
    }

    const response = await axios.get(`${API_BASE_URL}${API_PATHS.LIST_TASK_REVIEWS}`, {
      params,
      headers: getAuthHeaders()
    });

    if (response.data?.success) {
      reviewItems.value = response.data.items || [];
    } else {
      reviewItems.value = [];
      alert(response.data?.error || '获取复核列表失败');
    }
  } catch (error) {
    reviewItems.value = [];
    alert(error.response?.data?.error || error.message || '获取复核列表失败');
  } finally {
    loading.value = false;
  }
};

const loadTaskReview = async (taskId) => {
  const response = await axios.get(buildApiUrl(API_PATHS.GET_TASK_REVIEW, { taskId }), {
    headers: getAuthHeaders()
  });

  const review = response.data?.data;
  reviewForm.value = {
    reviewStatus: review?.reviewStatus || 'UNREVIEWED',
    riskLevel: review?.riskLevel || '',
    tags: review?.tags || '',
    remark: review?.remark || '',
    reviewResult: review?.reviewResult || ''
  };
};

const loadTaskResult = async (taskId) => {
  taskResultLoading.value = true;
  taskResultText.value = '';
  try {
    const response = await axios.get(buildApiUrl(API_PATHS.GET_TASK_RESULTS, { taskId }), {
      headers: getAuthHeaders()
    });

    if (response.data?.success || response.data?.code === 0) {
      const results = response.data.results || [];
      taskResultText.value = decodeUnicode(results[0]?.data || '');
    } else {
      taskResultText.value = response.data?.error || '暂无结果数据';
    }
  } catch (error) {
    taskResultText.value = error.response?.data?.error || error.message || '获取任务结果失败';
  } finally {
    taskResultLoading.value = false;
  }
};

const openDetail = async (item) => {
  selectedItem.value = { ...item };
  detailVisible.value = true;
  await Promise.all([
    loadTaskReview(item.taskId),
    loadTaskResult(item.taskId)
  ]);
};

const closeDetail = () => {
  detailVisible.value = false;
  selectedItem.value = null;
  taskResultText.value = '';
  reviewForm.value = {
    reviewStatus: 'UNREVIEWED',
    riskLevel: '',
    tags: '',
    remark: '',
    reviewResult: ''
  };
};

const saveReview = async () => {
  if (!selectedItem.value) return;

  saving.value = true;
  try {
    const response = await axios.post(
      buildApiUrl(API_PATHS.SAVE_TASK_REVIEW, { taskId: selectedItem.value.taskId }),
      reviewForm.value,
      { headers: getAuthHeaders() }
    );

    if (response.data?.success) {
      alert('复核已保存');
      await loadReviewItems();
      selectedItem.value = {
        ...selectedItem.value,
        reviewStatus: reviewForm.value.reviewStatus,
        riskLevel: reviewForm.value.riskLevel,
        tags: reviewForm.value.tags,
        remark: reviewForm.value.remark,
        reviewResult: reviewForm.value.reviewResult
      };
    } else {
      alert(response.data?.error || '保存复核失败');
    }
  } catch (error) {
    alert(error.response?.data?.error || error.message || '保存复核失败');
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadReviewItems();
});
</script>

<template>
  <div class="review-page">
    <div class="page-header">
      <div>
        <h1>复核工作台</h1>
        <p>对 AI 任务结果进行人工确认、标记风险和沉淀处理结论。</p>
      </div>
      <div class="header-tools">
        <select v-model="reviewStatusFilter" class="filter-select" @change="loadReviewItems">
          <option
            v-for="option in reviewStatusOptions"
            :key="option.value || 'all'"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
        <button class="btn btn-primary" @click="loadReviewItems" :disabled="loading">
          {{ loading ? '加载中...' : '刷新列表' }}
        </button>
      </div>
    </div>

    <div class="summary-bar">
      <span>当前共 {{ totalCount }} 条任务</span>
    </div>

    <div class="table-card">
      <table class="review-table">
        <thead>
          <tr>
            <th>任务ID</th>
            <th>任务状态</th>
            <th>处理进度</th>
            <th>复核状态</th>
            <th>风险等级</th>
            <th>更新时间</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="empty-cell">数据加载中...</td>
          </tr>
          <tr v-else-if="reviewItems.length === 0">
            <td colspan="8" class="empty-cell">暂无复核数据</td>
          </tr>
          <tr v-else v-for="item in reviewItems" :key="item.taskId">
            <td class="task-id">{{ item.taskId }}</td>
            <td>{{ taskStatusLabelMap[item.taskStatus] || item.taskStatus }}</td>
            <td>{{ item.taskProgress ?? 0 }}%</td>
            <td>{{ reviewStatusLabelMap[item.reviewStatus] || item.reviewStatus }}</td>
            <td>{{ riskLevelOptions.find(option => option.value === item.riskLevel)?.label || '-' }}</td>
            <td>{{ formatDate(item.reviewUpdateTime || item.taskUpdateTime) }}</td>
            <td class="remark-cell">{{ item.remark || '-' }}</td>
            <td>
              <button class="btn btn-primary btn-sm" @click="openDetail(item)">去复核</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="detailVisible && selectedItem" class="detail-mask" @click.self="closeDetail">
      <div class="detail-panel">
        <div class="detail-header">
          <h3>任务复核</h3>
          <button class="btn btn-outline btn-sm" @click="closeDetail">关闭</button>
        </div>

        <div class="detail-grid">
          <div class="meta-item">
            <label>任务ID</label>
            <span>{{ selectedItem.taskId }}</span>
          </div>
          <div class="meta-item">
            <label>任务状态</label>
            <span>{{ taskStatusLabelMap[selectedItem.taskStatus] || selectedItem.taskStatus }}</span>
          </div>
          <div class="meta-item">
            <label>创建时间</label>
            <span>{{ formatDate(selectedItem.taskCreateTime) }}</span>
          </div>
          <div class="meta-item">
            <label>更新时间</label>
            <span>{{ formatDate(selectedItem.taskUpdateTime) }}</span>
          </div>
        </div>

        <div class="detail-section">
          <label>任务结果</label>
          <div class="result-box">
            <div v-if="taskResultLoading">结果加载中...</div>
            <pre v-else>{{ taskResultText || '暂无结果数据' }}</pre>
          </div>
        </div>

        <div class="detail-section">
          <label>复核状态</label>
          <select v-model="reviewForm.reviewStatus" class="input">
            <option
              v-for="option in reviewStatusOptions.filter(option => option.value)"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </div>

        <div class="detail-section">
          <label>风险等级</label>
          <select v-model="reviewForm.riskLevel" class="input">
            <option
              v-for="option in riskLevelOptions"
              :key="option.value || 'none'"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </div>

        <div class="detail-section">
          <label>标签</label>
          <input v-model="reviewForm.tags" class="input" type="text" placeholder="如：重量异常, OCR不清晰" />
        </div>

        <div class="detail-section">
          <label>复核结论</label>
          <textarea
            v-model="reviewForm.reviewResult"
            class="textarea"
            rows="4"
            placeholder="填写复核结论"
          ></textarea>
        </div>

        <div class="detail-section">
          <label>复核备注</label>
          <textarea
            v-model="reviewForm.remark"
            class="textarea"
            rows="4"
            placeholder="填写复核备注"
          ></textarea>
        </div>

        <div class="detail-footer">
          <button class="btn btn-primary" @click="saveReview" :disabled="saving">
            {{ saving ? '保存中...' : '保存复核' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.review-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.summary-bar,
.table-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header h1 {
  margin: 0 0 8px;
  font-size: 24px;
  color: #264b86;
}

.page-header p {
  margin: 0;
  color: #5d7398;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-select,
.input,
.textarea {
  width: 100%;
  border: 1px solid #d8e5fb;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  background: #fff;
}

.review-table {
  width: 100%;
  border-collapse: collapse;
}

.review-table th,
.review-table td {
  padding: 14px 12px;
  border-bottom: 1px solid #edf2fb;
  text-align: left;
  font-size: 14px;
}

.review-table th {
  color: #4b6288;
  font-weight: 600;
  background: #f8fbff;
}

.task-id {
  max-width: 220px;
  word-break: break-all;
  color: #264b86;
  font-weight: 600;
}

.remark-cell {
  max-width: 260px;
  color: #5d7398;
}

.empty-cell {
  text-align: center;
  color: #6d7f99;
  padding: 40px 0;
}

.detail-mask {
  position: fixed;
  inset: 0;
  background: rgba(18, 38, 77, 0.35);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1200;
}

.detail-panel {
  width: 880px;
  max-height: 90vh;
  overflow: auto;
  background: #fff;
  border-radius: 18px;
  padding: 24px;
  box-shadow: 0 20px 60px rgba(23, 44, 88, 0.18);
}

.detail-header,
.detail-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.detail-header h3 {
  margin: 0;
  color: #264b86;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.meta-item {
  background: #f7fbff;
  border: 1px solid #deebff;
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item label,
.detail-section label {
  color: #587094;
  font-size: 13px;
  font-weight: 600;
}

.detail-section {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-box {
  background: #f2f7ff;
  border: 1px solid #d5e5ff;
  border-radius: 12px;
  padding: 14px;
  max-height: 280px;
  overflow: auto;
}

.result-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.btn {
  border: none;
  border-radius: 10px;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.btn-sm {
  padding: 8px 12px;
  font-size: 13px;
}

.btn-primary {
  background: linear-gradient(135deg, #4a6cf7, #3c63f5);
  color: #fff;
}

.btn-outline {
  background: #f6faff;
  border: 1px solid #d6e6ff;
  color: #315faa;
}
</style>
