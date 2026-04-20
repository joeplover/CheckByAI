<template>
  <div class="file-upload-container">
    <div class="main-content">
      <div class="header">
        <h1>智能文件处理</h1>
        <p class="description">上传 Excel 文件或选择本地数据，选择处理方式进行智能分析</p>
      </div>
      
      <div class="data-source-switch">
        <div class="switch-container">
          <label class="switch-label">
            <input type="checkbox" v-model="useLocalData" class="switch-input">
            <span class="switch-slider"></span>
          </label>
          <div class="mode-labels">
            <span :class="['mode-label', { active: !useLocalData }]">上传Excel文件</span>
            <span :class="['mode-label', { active: useLocalData }]">选择本地数据</span>
          </div>
        </div>
        <div class="mode-description">
          <p v-if="!useLocalData">上传Excel文件进行处理</p>
          <p v-else>从本地数据库中选择已存储的物流订单数据</p>
        </div>
      </div>
      
      <div class="processing-mode-switch">
        <div class="switch-container">
          <label class="switch-label">
            <input 
              type="checkbox" 
              v-model="useLangChain" 
              class="switch-input"
              :disabled="useLocalData"
              @change="handleModeChange"
            >
            <span class="switch-slider" :class="{ 'disabled': useLocalData }"></span>
          </label>
          <div class="mode-labels">
            <span :class="['mode-label', { active: !useLangChain }]">Coze工作流</span>
            <span :class="['mode-label', { active: useLangChain }]">LangChain Agent</span>
          </div>
        </div>
        <div class="mode-description">
          <p v-if="useLocalData" class="warning-text">本地数据模式仅支持 Coze 工作流</p>
          <p v-else-if="!useLangChain">使用 Coze 工作流进行文件处理，适合标准任务流程</p>
          <p v-else>使用 LangChain Agent 进行智能分析，适合复杂任务处理</p>
        </div>
      </div>
      
      <div class="grid-layout">
        <div class="left-section">
          <div class="upload-section" v-if="!useLocalData">
            <div class="upload-card">
              <div class="card-header">
                <h2>文件上传</h2>
                <p>支持 .xlsx 和 .xls 格式文件，最大 10MB</p>
              </div>
              
              <div class="upload-area" :class="{ 'dragover': isDragover }" 
                   @dragover.prevent="isDragover = true"
                   @dragleave.prevent="isDragover = false"
                   @drop.prevent="handleFileDrop">
                <input type="file" ref="fileInput" @change="handleFileSelect" 
                   accept=".xlsx,.xls" style="display: none;">
                <div class="upload-icon">📁</div>
                <h3>{{ selectedFile ? selectedFile.name : '点击或拖拽文件到此处上传' }}</h3>
                <p>{{ selectedFile ? '已选择文件: ' + selectedFile.name : '支持 .xlsx 和 .xls 格式' }}</p>
                <div class="upload-buttons">
                  <button @click="triggerFileInput" class="btn btn-primary" :disabled="isUploading">
                    <span class="icon">+</span> 选择文件
                  </button>
                  <button @click="uploadFile" class="btn btn-success btn-lg" :disabled="isUploading || !selectedFile">
                    <span v-if="isUploading">处理中...</span>
                    <span v-else><span class="icon">🚀</span> 开始处理</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
          
          <div class="local-data-section" v-else>
            <div class="local-data-card">
              <div class="card-header">
                <h2>本地数据选择</h2>
                <div class="header-actions">
                  <button @click="loadLocalData" class="btn btn-sm btn-outline" :disabled="isLoadingLocalData">
                    <span v-if="isLoadingLocalData">加载中...</span>
                    <span v-else><span class="icon">🔄</span> 刷新</span>
                  </button>
                </div>
              </div>
              
              <div class="local-search-box">
                <input 
                  v-model="localSearchKeyword" 
                  type="text" 
                  placeholder="搜索运单号、车牌号、地址..."
                  class="local-search-input"
                  @keyup.enter="searchLocalData"
                >
                <button @click="searchLocalData" class="btn btn-search-local">
                  <span class="icon">🔍</span> 搜索
                </button>
                <button v-if="localSearchKeyword" @click="clearLocalSearch" class="btn btn-clear">
                  清除
                </button>
              </div>
              
              <div class="local-data-content" v-if="localDataList.length > 0">
                <div class="select-all-bar">
                  <label class="checkbox-label">
                    <input type="checkbox" v-model="selectAllLocal" @change="toggleSelectAll">
                    <span>全选</span>
                  </label>
                  <span 
                    class="selected-count clickable" 
                    :class="{ 'has-selection': selectedLocalIds.length > 0 }"
                    @click="selectedLocalIds.length > 0 && (showSelectedPanel = !showSelectedPanel)"
                  >
                    已选择 {{ selectedLocalIds.length }} 条数据（共 {{ localDataList.length }} 条）
                    <span v-if="selectedLocalIds.length > 0" class="toggle-icon">{{ showSelectedPanel ? '▼' : '▶' }}</span>
                  </span>
                </div>
                
                <!-- 宸查€夋嫨鏁版嵁娴姩闈㈡澘 -->
                <transition name="slide-fade">
                  <div class="selected-panel" v-if="showSelectedPanel && selectedLocalIds.length > 0">
                    <div class="selected-panel-header">
                      <h4>已选择的数据（{{ selectedLocalIds.length }}条）</h4>
                      <button @click="clearAllSelected" class="btn btn-sm btn-danger">娓呯┖鍏ㄩ儴</button>
                    </div>
                    <div class="selected-panel-content">
                  <div 
                    v-for="item in selectedItemsData" 
                    :key="item._rowKey" 
                    class="selected-item-card"
                  >
                        <div class="selected-item-info">
                          <div class="item-main">
                            <span class="waybill-no">{{ item.waybillNo || '无运单号' }}</span>
                            <span class="plate-no">{{ item.transportPlateNo || '' }}</span>
                          </div>
                          <div class="item-address" v-if="item.loadingAddress || item.unloadingAddress">
                            <span class="address-item" v-if="item.loadingAddress">
                              <strong>装:</strong>{{ item.loadingAddress }}
                            </span>
                            <span class="address-item" v-if="item.unloadingAddress">
                              <strong>卸:</strong>{{ item.unloadingAddress }}
                            </span>
                          </div>
                        </div>
                        <button @click="removeSelectedItem(item._rowKey)" class="btn-remove">×</button>
                      </div>
                    </div>
                  </div>
                </transition>
                
                <div class="local-data-list">
                  <div 
                    v-for="item in localDataList" 
                    :key="item._rowKey" 
                    class="local-data-item"
                    :class="{ 'selected': selectedLocalIds.includes(item._rowKey) }"
                    @click="toggleSelectItem(item)"
                  >
                    <div class="item-checkbox">
                      <input 
                        type="checkbox" 
                        :checked="selectedLocalIds.includes(item._rowKey)" 
                        @change.stop="handleCheckboxChange(item, $event)"
                        @click.stop
                      >
                    </div>
                    <div class="item-content">
                      <div class="item-header">
                        <span class="waybill-no">{{ item.waybillNo || '无运单号' }}</span>
                        <span class="source-order">{{ item.sourceOrderNo || '' }}</span>
                      </div>
                      <div class="item-details">
                        <span class="detail-item">
                          <strong>装货:</strong> {{ item.loadingAddress || '-' }}
                        </span>
                        <span class="detail-item">
                          <strong>卸货:</strong> {{ item.unloadingAddress || '-' }}
                        </span>
                      </div>
                      <div class="item-footer">
                        <span class="plate-no">{{ item.transportPlateNo || '' }}</span>
                        <span class="cargo-type">{{ item.cargoMainType || '' }} {{ item.cargoSubType || '' }}</span>
                      </div>
                    </div>
                  </div>
                </div>
                
                <div class="submit-actions">
                  <button @click="submitLocalData" class="btn btn-success btn-lg" :disabled="isUploading || selectedLocalIds.length === 0">
                    <span v-if="isUploading">处理中...</span>
                    <span v-else><span class="icon">🚀</span> 提交选中数据（{{ selectedLocalIds.length }}条）</span>
                  </button>
                </div>
              </div>
              
              <div class="empty-local-data" v-else-if="!isLoadingLocalData">
                <div class="empty-icon">📦</div>
                <p v-if="localSearchKeyword">未找到匹配 "{{ localSearchKeyword }}" 的数据</p>
                <p v-else>暂无本地数据</p>
                <p class="hint" v-if="!localSearchKeyword">请先上传Excel文件导入数据</p>
              </div>
              
              <div class="loading-local-data" v-else>
                <div class="loading-icon">⏳</div>
                <p>加载本地数据中...</p>
              </div>
            </div>
          </div>
          
          <div class="result-section" v-if="taskId">
            <div class="result-card">
              <div class="card-header">
                <h2>处理结果</h2>
              </div>
              <div class="result-content">
                <div class="result-item">
                  <label>任务ID:</label>
                  <span class="task-id">{{ taskId }}</span>
                  <button @click="copyTaskId" class="btn btn-sm btn-outline">复制</button>
                </div>
                <div class="result-item">
                  <label>状态:</label>
                  <span class="status completed">已完成</span>
                </div>
                <div class="result-item">
                  <label>处理时间:</label>
                  <span>{{ processTime }}</span>
                </div>
              </div>
              <div class="result-actions">
                <button @click="resetUpload" class="btn btn-outline">
                  <span class="icon">🔄</span> 重新选择
                </button>
                <button @click="refreshTasks" class="btn btn-primary">
                  <span class="icon">🔄</span> 刷新任务列表
                </button>
              </div>
            </div>
          </div>
          
          <div class="tips-section">
            <div class="tips-card">
              <h3>💡 使用提示</h3>
              <ul>
                <li v-if="!useLocalData">请确保 Excel 文件格式正确，且包含必要字段</li>
                <li v-if="!useLocalData">文件大小不要超过 10MB，否则可能上传失败</li>
                <li v-if="useLocalData">选择本地数据后，点击提交按钮开始处理</li>
                <li v-if="useLocalData">支持多选数据进行批量提交</li>
                <li>处理时间取决于数据量和网络速度，请耐心等待</li>
                <li>处理完成后，结果会自动保存到系统中</li>
              </ul>
            </div>
          </div>
        </div>
        
        <div class="right-section">
          <div class="tasks-section">
            <div class="tasks-card">
              <div class="card-header">
                <h2>任务历史记录</h2>
                <div class="header-actions">
                  <select v-model="taskStatusFilter" @change="refreshTasks" class="task-filter-select">
                    <option v-for="option in TASK_FILTER_OPTIONS" :key="option.value" :value="option.value">
                      {{ option.label }}
                    </option>
                  </select>
                  <button @click="refreshTasks" class="btn btn-sm btn-outline" :disabled="isLoadingTasks">
                    <span v-if="isLoadingTasks">加载中...</span>
                    <span v-else><span class="icon">🔄</span> 刷新</span>
                  </button>
                </div>
              </div>
              
              <div class="tasks-list" v-if="tasks.length > 0">
                <div 
                  v-for="task in tasks" 
                  :key="task.id" 
                  class="task-item"
                  :class="{ 'active': selectedTask && selectedTask.id === task.id && taskResultModalVisible }"
                  @click="openTaskModal(task)"
                >
                  <div class="task-header">
                    <span class="task-id-small">{{ task.taskId }}</span>
                    <div class="task-header-actions">
                      <span :class="['task-status', task.status.toLowerCase()]">{{ task.status }}</span>
                      <button
                        v-if="canCancelTask(task)"
                        @click.stop="cancelTask(task.taskId)"
                        class="btn btn-sm btn-warning"
                      >
                        取消
                      </button>
                      <button
                        v-if="canRetryTask(task)"
                        @click.stop="retryTask(task.taskId)"
                        class="btn btn-sm btn-secondary"
                      >
                        重试
                      </button>
                      <button @click.stop="deleteTask(task.taskId)" class="btn btn-sm btn-danger delete-btn">
                        <span class="icon">×</span>
                      </button>
                    </div>
                  </div>
                  <div class="task-info">
                    <span class="task-time">{{ formatDate(task.createTime) }}</span>
                    <span class="task-user">用户: {{ task.userId }}</span>
                  </div>
                  <div class="task-progress">
                    <div class="progress-bar">
                      <div class="progress-fill" :style="{ width: task.progress + '%' }"></div>
                    </div>
                    <span class="progress-text">{{ task.progress }}%</span>
                  </div>
                </div>
              </div>
              
              <div class="empty-tasks" v-else-if="!isLoadingTasks">
                <div class="empty-icon">📋</div>
                <p>暂无任务记录</p>
                <button @click="resetUpload" class="btn btn-primary">
                  <span class="icon">+</span> 上传新文件
                </button>
              </div>
              
              <div class="loading-tasks" v-else>
                <div class="loading-icon">⏳</div>
                <p>加载任务记录中...</p>
              </div>
            </div>
          </div>
          
          
        </div>
      </div>
    </div>
    
    <div
      v-if="taskResultModalVisible && selectedTask"
      class="task-modal-mask"
      @click.self="closeTaskModal"
    >
      <div class="task-modal">
        <div class="task-modal-header">
          <h3>任务处理结果</h3>
          <button @click="closeTaskModal" class="btn btn-sm btn-outline">关闭</button>
        </div>

        <div class="task-modal-body">
          <div class="task-meta-grid">
            <div class="meta-item">
              <label>任务ID</label>
              <span class="task-id">{{ selectedTask.taskId }}</span>
            </div>
            <div class="meta-item">
            <label>状态</label>
              <span :class="['status', selectedTask.status.toLowerCase()]">{{ selectedTask.status }}</span>
            </div>
            <div class="meta-item">
              <label>创建时间</label>
              <span>{{ formatDate(selectedTask.createTime) }}</span>
            </div>
            <div class="meta-item">
              <label>更新时间</label>
              <span>{{ formatDate(selectedTask.updateTime) }}</span>
            </div>
          </div>

          <div class="task-modal-progress">
            <label>处理进度</label>
            <div class="progress-bar large">
              <div class="progress-fill" :style="{ width: selectedTask.progress + '%' }"></div>
            </div>
            <span class="progress-text">{{ selectedTask.progress }}%</span>
          </div>

          <div class="task-modal-result">
            <div class="result-title-row">
              <label>处理结果</label>
              <div class="result-view-toggle" v-if="taskResultText">
                <button
                  type="button"
                  :class="['toggle-btn', { active: resultViewMode === 'structured' }]"
                  @click="resultViewMode = 'structured'"
                >
                  结构化结果
                </button>
                <button
                  type="button"
                  :class="['toggle-btn', { active: resultViewMode === 'raw' }]"
                  @click="resultViewMode = 'raw'"
                >
                  原始回调数据
                </button>
              </div>
            </div>
            <div v-if="taskResultLoading" class="task-result-loading">结果加载中...</div>
            <div v-else class="result-data">
              <pre v-if="resultViewMode === 'raw'">{{ taskResultText || '暂无结果数据' }}</pre>
              <div v-else-if="structuredTaskResult.length > 0" class="structured-result-list">
                <div
                  v-for="(item, index) in structuredTaskResult"
                  :key="`${item.table['运单号'] || index}-${index}`"
                  class="structured-result-card"
                >
                  <div class="structured-result-header">
                    <div>
                      <span class="result-index">#{{ index + 1 }}</span>
                      <span class="result-waybill">{{ item.table['运单号'] || '未知运单' }}</span>
                    </div>
                    <span :class="['result-badge', item.table['异常信息'] ? 'warning' : 'success']">
                      {{ item.table['异常信息'] ? '存在异常' : '正常' }}
                    </span>
                  </div>

                  <div v-if="item.table['异常信息']" class="result-warning">
                    {{ item.table['异常信息'] }}
                  </div>

                  <div class="result-section-card">
                    <h4>表格数据</h4>
                    <div class="result-field-grid">
                      <div v-for="field in resultDisplayFields" :key="field" class="result-field">
                        <span class="field-label">{{ field }}</span>
                        <span class="field-value">{{ item.table[field] || '-' }}</span>
                      </div>
                    </div>
                  </div>

                  <div class="ocr-grid">
                    <div class="result-section-card">
                      <h4>装货 OCR</h4>
                      <div class="result-field-list">
                        <div v-for="(value, key) in item.loadingOcr" :key="key" class="result-field-row">
                          <span class="field-label">{{ key }}</span>
                          <span class="field-value">{{ value || '-' }}</span>
                        </div>
                      </div>
                    </div>
                    <div class="result-section-card">
                      <h4>卸货 OCR</h4>
                      <div class="result-field-list">
                        <div v-for="(value, key) in item.unloadingOcr" :key="key" class="result-field-row">
                          <span class="field-label">{{ key }}</span>
                          <span class="field-value">{{ value || '-' }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <pre v-else>{{ taskResultText || '暂无结果数据' }}</pre>
            </div>
          </div>

          <div class="task-ai-actions">
            <input
              v-model="aiQuestion"
              class="ai-question-input"
              type="text"
              placeholder="可输入你想让 AI 分析的问题（可选）"
              :disabled="taskResultLoading || aiAnalyzing"
            >
            <button
              @click="askAiForTaskAnalysis"
              class="btn btn-primary"
              :disabled="taskResultLoading || aiAnalyzing"
            >
              <span v-if="aiAnalyzing">AI 分析中...</span>
              <span v-else>向 AI 提问</span>
            </button>
          </div>

          <div class="task-ai-result" v-if="aiAnalysis || aiAnalysisError || aiAnalyzing">
            <label>AI 数据分析</label>
            <div v-if="aiAnalyzing" class="task-result-loading">AI 正在分析当前任务结果...</div>
            <div v-else-if="aiAnalysisError" class="ai-error">{{ aiAnalysisError }}</div>
            <div v-else class="result-data ai-analysis-box">
              <pre>{{ aiAnalysis }}</pre>
            </div>
          </div>

          <div class="task-review-panel">
            <div class="review-header">
              <label>人工复核</label>
              <button
                @click="saveTaskReview"
                class="btn btn-primary btn-sm"
                :disabled="taskReviewLoading || taskReviewSaving || !selectedTask"
              >
                {{ taskReviewSaving ? '保存中...' : '保存复核' }}
              </button>
            </div>
            <div v-if="taskReviewLoading" class="task-result-loading">复核信息加载中...</div>
            <div v-else class="review-form-grid">
              <div class="meta-item">
                <label>复核状态</label>
                <select v-model="taskReview.reviewStatus" class="review-input">
                  <option
                    v-for="option in REVIEW_STATUS_OPTIONS"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </div>
              <div class="meta-item">
                <label>风险等级</label>
                <select v-model="taskReview.riskLevel" class="review-input">
                  <option
                    v-for="option in REVIEW_RISK_OPTIONS"
                    :key="option.value || 'none'"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </div>
              <div class="review-form-full">
                <label>标签</label>
                <input
                  v-model="taskReview.tags"
                  class="review-input"
                  type="text"
                  placeholder="如：重量异常, 待人工确认"
                >
              </div>
              <div class="review-form-full">
                <label>复核结论</label>
                <textarea
                  v-model="taskReview.reviewResult"
                  class="review-textarea"
                  rows="3"
                  placeholder="填写复核结论"
                ></textarea>
              </div>
              <div class="review-form-full">
                <label>复核备注</label>
                <textarea
                  v-model="taskReview.remark"
                  class="review-textarea"
                  rows="3"
                  placeholder="填写补充说明"
                ></textarea>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, computed } from 'vue';
import { useRouter } from 'vue-router';
import { API_PATHS, buildApiUrl } from '../config/api';

const router = useRouter();

const isDragover = ref(false);
const selectedFile = ref(null);
const isUploading = ref(false);
const taskId = ref('');
const processTime = ref('');
const fileInput = ref(null);
const useLangChain = ref(false);
const useLocalData = ref(false);

const localDataList = ref([]);
const selectedLocalIds = ref([]);
const selectedItemsMap = ref(new Map());
const selectAllLocal = ref(false);
const isLoadingLocalData = ref(false);
const localSearchKeyword = ref('');
const showSelectedPanel = ref(false);

const normalizeOrderId = (item) => {
  const rawId = item?.id ?? item?.orderId ?? item?.logisticsId ?? item?.logisticsOrderId;
  if (rawId === null || rawId === undefined || rawId === '') {
    return null;
  }
  const parsedId = Number(rawId);
  return Number.isFinite(parsedId) ? parsedId : null;
};

const buildLocalItemKey = (item, index) => {
  const orderId = normalizeOrderId(item);
  const waybillNo = item?.waybillNo || 'no-waybill';
  const sourceOrderNo = item?.sourceOrderNo || 'no-source';
  return [
    'local',
    orderId ?? 'no-id',
    waybillNo,
    sourceOrderNo,
    index
  ].join('-');
};

const normalizeLocalDataList = (items) => {
  return (items || []).map((item, index) => ({
    ...item,
    _orderId: normalizeOrderId(item),
    _rowKey: buildLocalItemKey(item, index)
  }));
};

const syncSelectedStateWithLocalData = () => {
  const validKeys = new Set(localDataList.value.map(item => item._rowKey));
  selectedLocalIds.value = selectedLocalIds.value.filter(key => validKeys.has(key));

  const nextMap = new Map();
  localDataList.value.forEach((item) => {
    if (selectedLocalIds.value.includes(item._rowKey)) {
      nextMap.set(item._rowKey, item);
    }
  });
  selectedItemsMap.value = nextMap;
  selectAllLocal.value = localDataList.value.length > 0
    && localDataList.value.every(item => selectedLocalIds.value.includes(item._rowKey));
};

const setLocalItemSelected = (item, checked) => {
  const rowKey = item._rowKey;
  const index = selectedLocalIds.value.indexOf(rowKey);

  if (checked && index === -1) {
    selectedLocalIds.value.push(rowKey);
    selectedItemsMap.value.set(rowKey, item);
  }

  if (!checked && index > -1) {
    selectedLocalIds.value.splice(index, 1);
    selectedItemsMap.value.delete(rowKey);
  }

  selectAllLocal.value = localDataList.value.length > 0
    && localDataList.value.every(dataItem => selectedLocalIds.value.includes(dataItem._rowKey));
};

watch(useLocalData, (newVal) => {
  if (newVal && useLangChain.value) {
    useLangChain.value = false;
  }
});

const handleModeChange = () => {
  if (useLocalData.value && useLangChain.value) {
    useLangChain.value = false;
  }
};

const tasks = ref([]);
const selectedTask = ref(null);
const isLoadingTasks = ref(false);
const taskResultModalVisible = ref(false);
const taskResultLoading = ref(false);
const resultViewMode = ref('structured');
const aiAnalyzing = ref(false);
const aiAnalysis = ref('');
const aiAnalysisError = ref('');
const aiQuestion = ref('');
const taskStatusFilter = ref('ALL');
const taskReviewLoading = ref(false);
const taskReviewSaving = ref(false);
const taskReview = reactive({
  reviewStatus: 'UNREVIEWED',
  riskLevel: '',
  tags: '',
  remark: '',
  reviewResult: ''
});
const TASK_FILTER_OPTIONS = [
  { label: '全部', value: 'ALL' },
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' }
];
const REVIEW_STATUS_OPTIONS = [
  { label: '未复核', value: 'UNREVIEWED' },
  { label: '复核中', value: 'REVIEWING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' }
];
const REVIEW_RISK_OPTIONS = [
  { label: '未设置', value: '' },
  { label: '低风险', value: 'LOW' },
  { label: '中风险', value: 'MEDIUM' },
  { label: '高风险', value: 'HIGH' },
  { label: '严重', value: 'CRITICAL' }
];
const resultDisplayFields = [
  '装货重量',
  '卸货重量',
  '毛重',
  '皮重',
  '净重',
  '车牌号'
];

const taskResultText = computed(() => {
  if (!selectedTask.value) {
    return '';
  }
  return decodeUnicode(selectedTask.value.dataContent || selectedTask.value.data || '');
});

const structuredTaskResult = computed(() => parseStructuredResult(taskResultText.value));

const loadLocalData = async () => {
  isLoadingLocalData.value = true;
  try {
    const token = localStorage.getItem('token');
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    let url = buildApiUrl(API_PATHS.GET_LOGISTICS_LIST);
    if (localSearchKeyword.value && localSearchKeyword.value.trim()) {
      url += `?keyword=${encodeURIComponent(localSearchKeyword.value.trim())}`;
    }

    const response = await fetch(url, {
      method: 'GET',
      headers: headers,
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.code === 0) {
      localDataList.value = normalizeLocalDataList(result.data);
      syncSelectedStateWithLocalData();
    } else {
      console.error('获取本地数据失败:', result.message || '未知错误');
      localDataList.value = [];
      syncSelectedStateWithLocalData();
    }
  } catch (error) {
    console.error('获取本地数据失败:', error);
    localDataList.value = [];
    syncSelectedStateWithLocalData();
  } finally {
    isLoadingLocalData.value = false;
  }
};

const searchLocalData = () => {
  loadLocalData();
};

const clearLocalSearch = () => {
  localSearchKeyword.value = '';
  loadLocalData();
};

const selectedItemsData = computed(() => {
  return selectedLocalIds.value.map(rowKey => selectedItemsMap.value.get(rowKey)).filter(Boolean);
});

const removeSelectedItem = (rowKey) => {
  const index = selectedLocalIds.value.indexOf(rowKey);
  if (index > -1) {
    selectedLocalIds.value.splice(index, 1);
    selectedItemsMap.value.delete(rowKey);
  }
  selectAllLocal.value = localDataList.value.length > 0
    && localDataList.value.every(item => selectedLocalIds.value.includes(item._rowKey));
};

const clearAllSelected = () => {
  selectedLocalIds.value = [];
  selectedItemsMap.value.clear();
  selectAllLocal.value = false;
};

const toggleSelectAll = () => {
  if (selectAllLocal.value) {
    localDataList.value.forEach(item => {
      if (!selectedLocalIds.value.includes(item._rowKey)) {
        selectedLocalIds.value.push(item._rowKey);
        selectedItemsMap.value.set(item._rowKey, item);
      }
    });
  } else {
    localDataList.value.forEach(item => {
      selectedItemsMap.value.delete(item._rowKey);
    });
    selectedLocalIds.value = [];
  }
};

const toggleSelectItem = (item) => {
  const checked = !selectedLocalIds.value.includes(item._rowKey);
  setLocalItemSelected(item, checked);
};

const handleCheckboxChange = (item, event) => {
  setLocalItemSelected(item, event.target.checked);
};

const submitLocalData = async () => {
  if (selectedLocalIds.value.length === 0) {
    alert('请选择要提交的数据');
    return;
  }

  isUploading.value = true;
  const startTime = new Date();

  try {
    const token = localStorage.getItem('token');
    const headers = {
      'Content-Type': 'application/json'
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const orderIds = [...new Set(
      selectedItemsData.value
        .map(item => item._orderId)
        .filter(orderId => orderId !== null)
    )];
    const waybillNos = [...new Set(
      selectedItemsData.value
        .map(item => (item.waybillNo || '').trim())
        .filter(Boolean)
    )];

    if (orderIds.length === 0 && waybillNos.length === 0) {
      alert('当前选中的数据缺少有效订单标识，无法提交，请先刷新本地数据后重试');
      return;
    }

    const response = await fetch(buildApiUrl(API_PATHS.SUBMIT_LOCAL_DATA), {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        orderIds,
        waybillNos,
        mode: useLangChain.value ? 'langchain' : 'coze'
      }),
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();

    if (result.success || result.code === 0) {
      taskId.value = result.taskId;
      const endTime = new Date();
      const duration = (endTime - startTime) / 1000;
      processTime.value = `${duration.toFixed(2)}s`;
      await refreshTasks();
      selectedLocalIds.value = [];
      selectAllLocal.value = false;
      alert(result.message || '提交成功');

    } else {
      alert('提交失败: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('提交失败:', error);
    alert('提交失败: ' + error.message);
  } finally {
    isUploading.value = false;
  }
};

const triggerFileInput = () => {
  fileInput.value.click();
};

const handleFileSelect = (event) => {
  const file = event.target.files[0];
  if (file) {
    selectedFile.value = file;
  }
};

const handleFileDrop = (event) => {
  isDragover.value = false;
  const file = event.dataTransfer.files[0];
  if (file && (file.name.endsWith('.xlsx') || file.name.endsWith('.xls'))) {
    selectedFile.value = file;
  }
};

// 上传文件
const uploadFile = async () => {
  if (!selectedFile.value) {
    alert('\u8bf7\u5148\u9009\u62e9\u6587\u4ef6');
    return;
  }

  isUploading.value = true;
  const startTime = new Date();

  try {
    const formData = new FormData();
    formData.append('file', selectedFile.value);

    const token = localStorage.getItem('token');
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const apiPath = useLangChain.value ? API_PATHS.UPLOAD_EXCEL_LANGCHAIN : API_PATHS.UPLOAD_EXCEL;
    const response = await fetch(buildApiUrl(apiPath), {
      method: 'POST',
      body: formData,
      headers,
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('\u767b\u5f55\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.success || result.code === 0) {
      taskId.value = result.taskId;
      const endTime = new Date();
      const duration = (endTime - startTime) / 1000;
      processTime.value = `${duration.toFixed(2)}s`;
      await refreshTasks();
      alert(result.message || '\u6587\u4ef6\u5904\u7406\u6210\u529f');

    } else {
      alert('\u5904\u7406\u5931\u8d25: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('\u4e0a\u4f20\u5931\u8d25:', error);
    alert('\u4e0a\u4f20\u5931\u8d25: ' + error.message);
  } finally {
    isUploading.value = false;
  }
};

const copyTaskId = () => {
  if (taskId.value) {
    navigator.clipboard.writeText(taskId.value).then(() => {
      alert('任务ID已复制');
    });
  }
};
// 重置上传
const resetUpload = () => {
  selectedFile.value = null;
  taskId.value = '';
  processTime.value = '';
  fileInput.value.value = '';
};

const refreshTasks = async () => {
  isLoadingTasks.value = true;
  try {
    // 闁兼儳鍢茶ぐ鍣抩ken
    const token = localStorage.getItem('token');

    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const tasksUrl = taskStatusFilter.value === 'ALL'
      ? buildApiUrl(API_PATHS.GET_TASKS)
      : `${buildApiUrl(API_PATHS.GET_TASKS)}?status=${encodeURIComponent(taskStatusFilter.value)}`;

    const response = await fetch(tasksUrl, {
      method: 'GET',
      headers: headers,
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();

    if (result.success || result.code === 0) {
      tasks.value = result.tasks || [];
      if (selectedTask.value) {
        const latestTask = tasks.value.find(task => task.id === selectedTask.value.id);
        if (!latestTask) {
          closeTaskModal();
        } else {
          selectedTask.value = {
            ...latestTask,
            dataContent: selectedTask.value.dataContent || latestTask.dataContent
          };
        }
      }
    } else {
      console.error('获取任务列表失败:', result.error || result.message);
      tasks.value = [];
    }
  } catch (error) {
    console.error('获取任务列表失败:', error);
    tasks.value = [];
  } finally {
    isLoadingTasks.value = false;
  }
};

// 闁瑰灚鎸哥槐鎴炵鐠囨彃顫ょ紓浣规尰閻忓顕ｉ崷顓犲炊
const openTaskModal = async (task) => {
  selectedTask.value = { ...task };
  taskResultModalVisible.value = true;
  taskResultLoading.value = true;
  taskReviewLoading.value = true;
  aiAnalyzing.value = false;
  aiAnalysis.value = '';
  aiAnalysisError.value = '';
  aiQuestion.value = '';
  resultViewMode.value = 'structured';
  resetTaskReview();
  const [resultData] = await Promise.all([
    fetchTaskResults(task.taskId),
    fetchTaskReview(task.taskId)
  ]);
  if (selectedTask.value && selectedTask.value.taskId === task.taskId && resultData !== null) {
    selectedTask.value.dataContent = resultData;
  }
  taskResultLoading.value = false;
  taskReviewLoading.value = false;
};

const fetchTaskResults = async (taskId) => {
  try {
    // 闁兼儳鍢茶ぐ鍣抩ken
    const token = localStorage.getItem('token');

    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(buildApiUrl(API_PATHS.GET_TASK_RESULTS.replace(':taskId', taskId)), {
      method: 'GET',
      headers: headers,
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return null;
    }

    const result = await response.json();

    if (result.success || result.code === 0) {
      const results = result.results || [];
      if (results.length > 0) {
        return results[0].data;
      }
      return '';
    } else {
      console.error('获取任务结果失败:', result.error || result.message);
      return null;
    }
  } catch (error) {
    console.error('获取任务结果失败:', error);
    return null;
  }
};

const resetTaskReview = () => {
  taskReview.reviewStatus = 'UNREVIEWED';
  taskReview.riskLevel = '';
  taskReview.tags = '';
  taskReview.remark = '';
  taskReview.reviewResult = '';
};

const fetchTaskReview = async (taskId) => {
  try {
    const token = localStorage.getItem('token');
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(buildApiUrl(API_PATHS.GET_TASK_REVIEW, { taskId }), {
      method: 'GET',
      headers,
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.success && result.data) {
      taskReview.reviewStatus = result.data.reviewStatus || 'UNREVIEWED';
      taskReview.riskLevel = result.data.riskLevel || '';
      taskReview.tags = result.data.tags || '';
      taskReview.remark = result.data.remark || '';
      taskReview.reviewResult = result.data.reviewResult || '';
    }
  } catch (error) {
    console.error('获取复核信息失败:', error);
  }
};

const saveTaskReview = async () => {
  if (!selectedTask.value) {
    return;
  }

  taskReviewSaving.value = true;
  try {
    const token = localStorage.getItem('token');
    const headers = {
      'Content-Type': 'application/json'
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(buildApiUrl(API_PATHS.SAVE_TASK_REVIEW, { taskId: selectedTask.value.taskId }), {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify(taskReview)
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.success) {
      alert('复核已保存');
    } else {
      alert(result.error || '保存复核失败');
    }
  } catch (error) {
    alert(`保存复核失败: ${error.message}`);
  } finally {
    taskReviewSaving.value = false;
  }
};

// 闁告帞濞€濞呭孩绂掔拠鎻掝潳
const deleteTask = async (taskId) => {
  if (!confirm('确定要删除这个任务吗？')) {
    return;
  }

  try {
    // 闁兼儳鍢茶ぐ鍣抩ken
    const token = localStorage.getItem('token');

    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // 閻犲鍟伴弫銈夊礆閻樼粯鐝熷ù鐘侯嚙婵喖鎯冮崙姗甀
    const response = await fetch(buildApiUrl(`/api/task/${taskId}`), {
      method: 'DELETE',
      headers: headers,
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();

    if (result.success || result.status === 'success') {
      if (selectedTask.value && selectedTask.value.taskId === taskId) {
        closeTaskModal();
      }
      await refreshTasks();
      alert('任务删除成功');
    } else {
      alert('删除失败: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('删除任务失败:', error);
    alert('删除失败: ' + error.message);
  }
};

const canCancelTask = (task) => {
  if (!task || !task.status) {
    return false;
  }
  const status = task.status.toUpperCase();
  return status === 'PENDING' || status === 'SENT' || status === 'PROCESSING';
};

const canRetryTask = (task) => {
  if (!task || !task.status) {
    return false;
  }
  const status = task.status.toUpperCase();
  return status === 'FAILED' || status === 'CANCELLED';
};

const cancelTask = async (taskId) => {
  if (!confirm('确定要取消该任务吗？')) {
    return;
  }

  try {
    const token = localStorage.getItem('token');
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(buildApiUrl(API_PATHS.CANCEL_TASK.replace(':taskId', taskId)), {
      method: 'POST',
      headers: headers,
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.success) {
      await refreshTasks();
      alert('任务已取消');
    } else {
      alert('取消失败: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('取消任务失败:', error);
    alert('取消失败: ' + error.message);
  }
};

const retryTask = async (taskId) => {
  if (!confirm('确定要重试该任务吗？')) {
    return;
  }

  try {
    const token = localStorage.getItem('token');
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(buildApiUrl(API_PATHS.RETRY_TASK.replace(':taskId', taskId)), {
      method: 'POST',
      headers: headers,
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.success) {
      await refreshTasks();
      alert(`重试已提交，新任务ID: ${result.newTaskId}`);
    } else {
      alert('重试失败: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('重试任务失败:', error);
    alert('重试失败: ' + error.message);
  }
};

const askAiForTaskAnalysis = async () => {
  if (!selectedTask.value) {
    return;
  }

  const resultText = decodeUnicode(selectedTask.value.dataContent || selectedTask.value.data || '');
  if (!resultText) {
    aiAnalysisError.value = '当前任务暂无可分析的结果数据';
    aiAnalysis.value = '';
    return;
  }

  aiAnalyzing.value = true;
  aiAnalysisError.value = '';
  aiAnalysis.value = '';

  try {
    const token = localStorage.getItem('token');
    const headers = {
      'Content-Type': 'application/json'
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const customQuestion = (aiQuestion.value || '').trim();
    const instruction = customQuestion
      ? `请基于以下任务处理结果，重点回答这个问题：${customQuestion}`
      : '请对以下任务处理结果进行数据分析，并按“关键结论、异常点、风险提示、优化建议”四部分输出';
    const prompt = `${instruction}\n\n任务ID：${selectedTask.value.taskId}\n\n处理结果：\n${resultText.slice(0, 12000)}`;

    const response = await fetch(buildApiUrl(API_PATHS.AI_CHAT), {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify({ message: prompt })
    });

    if (response.status === 401) {
      alert('登录已过期，请重新登录');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.code === 0 && result.data && result.data.response) {
      aiAnalysis.value = result.data.response;
    } else {
      aiAnalysisError.value = result.message || 'AI 分析失败，请稍后重试';
    }
  } catch (error) {
    aiAnalysisError.value = `AI 分析失败: ${error.message}`;
  } finally {
    aiAnalyzing.value = false;
  }
};

const closeTaskModal = () => {
  taskResultModalVisible.value = false;
  taskResultLoading.value = false;
  taskReviewLoading.value = false;
  taskReviewSaving.value = false;
  aiAnalyzing.value = false;
  aiAnalysis.value = '';
  aiAnalysisError.value = '';
  aiQuestion.value = '';
  resetTaskReview();
  selectedTask.value = null;
};

const formatDate = (dateString) => {
  if (!dateString) return '';
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};

const decodeUnicode = (str) => {
  if (!str) return '';
  try {
    return str;
  } catch (error) {
    return str;
  }
};

const normalizeResultText = (text) => {
  return (text || '')
    .trim()
    .replace(/^\s*\{\s*"data"\s*:\s*/, '')
    .replace(/\s*\}\s*$/, '')
    .trim();
};

const parseSectionFields = (fieldsText) => {
  const fields = {};
  if (!fieldsText) {
    return fields;
  }

  fieldsText
    .split(/[,，]\s*(?=[^,，:：()]+[:：])/)
    .forEach((part) => {
      const separatorIndex = part.search(/[:：]/);
      if (separatorIndex === -1) {
        return;
      }
      const key = part.slice(0, separatorIndex).trim();
      const value = part.slice(separatorIndex + 1).trim();
      if (key) {
        fields[key] = value;
      }
    });
  return fields;
};

const parseStructuredResult = (text) => {
  const normalizedText = normalizeResultText(text);
  if (!normalizedText) {
    return [];
  }

  const sectionRegex = /(表格|装货 OCR|卸货 OCR)\s*\(([\s\S]*?)\)(?=\s*(?:表格|装货 OCR|卸货 OCR)\s*\(|\s*$)/g;
  const sections = [];
  let match;
  while ((match = sectionRegex.exec(normalizedText)) !== null) {
    sections.push({
      type: match[1],
      fields: parseSectionFields(match[2])
    });
  }

  if (sections.length === 0) {
    return [];
  }

  const records = [];
  let currentRecord = null;
  sections.forEach((section) => {
    if (section.type === '表格') {
      currentRecord = {
        table: section.fields,
        loadingOcr: {},
        unloadingOcr: {}
      };
      records.push(currentRecord);
      return;
    }

    if (!currentRecord) {
      return;
    }

    if (section.type === '装货 OCR') {
      currentRecord.loadingOcr = section.fields;
    }
    if (section.type === '卸货 OCR') {
      currentRecord.unloadingOcr = section.fields;
    }
  });

  return records.filter(record => Object.keys(record.table).length > 0);
};

onMounted(() => {
  refreshTasks();
});

watch(useLocalData, (newVal) => {
  if (newVal && localDataList.value.length === 0) {
    loadLocalData();
  }
});
</script>

<style scoped>
.file-upload-container {
  padding: 0;
  background-color: #f5f7fa;
  height: 100%;
  width: 100%;
  margin: 0;
}

.main-content {
  padding: 0;
  width: 100%;
  box-sizing: border-box;
}

.header {
  text-align: center;
  margin-bottom: 40px;
  padding: 40px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.header h1 {
  font-size: 2.5rem;
  margin-bottom: 10px;
  font-weight: 600;
}

.description {
  font-size: 1.1rem;
  opacity: 0.9;
  margin: 0;
}

.processing-mode-switch {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.switch-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.switch-label {
  position: relative;
  display: inline-block;
  width: 60px;
  height: 34px;
}

.switch-input {
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #ccc;
  transition: .4s;
  border-radius: 34px;
}

.switch-slider.disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.switch-input:disabled + .switch-slider {
  cursor: not-allowed;
  opacity: 0.5;
}

.switch-slider:before {
  position: absolute;
  content: "";
  height: 26px;
  width: 26px;
  left: 4px;
  bottom: 4px;
  background-color: white;
  transition: .4s;
  border-radius: 50%;
}

.switch-input:checked + .switch-slider {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.switch-input:focus + .switch-slider {
  box-shadow: 0 0 1px #667eea;
}

.switch-input:checked + .switch-slider:before {
  transform: translateX(26px);
}

.mode-labels {
  display: flex;
  gap: 20px;
  align-items: center;
}

.mode-label {
  font-size: 1.1rem;
  font-weight: 500;
  color: #666;
  transition: all 0.3s ease;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
}

.mode-label.active {
  color: #667eea;
  background-color: rgba(102, 126, 234, 0.1);
  font-weight: 600;
}

.mode-description {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e0e0e0;
}

.mode-description p {
  color: #666;
  font-size: 0.95rem;
  margin: 0;
}

.mode-description .warning-text {
  color: #ff6b6b;
  font-weight: 500;
}

.grid-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 30px;
  margin-bottom: 40px;
}

.left-section {
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.right-section {
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.upload-section {
  flex: 0 0 auto;
}

.upload-card {
  background: white;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.upload-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}

.card-header {
  margin-bottom: 25px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  font-size: 1.5rem;
  margin: 0;
  color: #333;
}

.card-header p {
  color: #666;
  margin: 5px 0 0 0;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.task-filter-select {
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  color: #333;
  background: #fff;
  font-size: 0.9rem;
}

.upload-area {
  border: 2px dashed #e0e0e0;
  border-radius: 8px;
  padding: 60px 20px;
  text-align: center;
  transition: all 0.3s ease;
  cursor: pointer;
}

.upload-area:hover {
  border-color: #667eea;
  background-color: rgba(102, 126, 234, 0.05);
}

.upload-area.dragover {
  border-color: #667eea;
  background-color: rgba(102, 126, 234, 0.1);
}

.upload-icon {
  font-size: 4rem;
  margin-bottom: 20px;
  opacity: 0.5;
}

.upload-area h3 {
  font-size: 1.2rem;
  margin-bottom: 10px;
  color: #333;
}

.upload-area p {
  color: #666;
  margin-bottom: 20px;
}

.upload-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  align-items: center;
  margin-top: 20px;
  flex-wrap: wrap;
}

.process-section {
  margin-top: 30px;
  text-align: center;
}

.result-section {
  flex: 0 0 auto;
}

.result-card {
  background: white;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.result-content {
  margin: 20px 0;
}

.result-item {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
  padding: 12px;
  background-color: #f9f9f9;
  border-radius: 6px;
}

.result-item label {
  width: 120px;
  font-weight: 500;
  color: #555;
}

.result-item span {
  flex: 1;
  color: #333;
}

.task-id {
  font-family: 'Courier New', monospace;
  font-weight: 500;
  color: #667eea;
}

.task-id-small {
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
  font-weight: 500;
  color: #667eea;
  word-break: break-all;
}

.status {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 0.9rem;
  font-weight: 500;
}

.status.completed {
  background-color: #d4edda;
  color: #155724;
}

.status.pending {
  background-color: #fff3cd;
  color: #856404;
}

.status.failed {
  background-color: #f8d7da;
  color: #721c24;
}

.status.processing,
.status.sent {
  background-color: #d1ecf1;
  color: #0c5460;
}

.status.cancelled {
  background-color: #ececec;
  color: #555;
}

.result-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 30px;
}

.tips-section {
  flex: 0 0 auto;
}

.tips-card {
  background: white;
  border-radius: 12px;
  padding: 25px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.tips-card h3 {
  margin-bottom: 15px;
  color: #333;
}

.tips-card ul {
  list-style: none;
  padding: 0;
}

.tips-card li {
  padding: 8px 0;
  padding-left: 20px;
  position: relative;
  color: #666;
}

.tips-card li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #667eea;
  font-weight: bold;
}

/* 濞寸姾顕ф慨鐔煎礆濡ゅ嫨鈧啴寮藉畡鎵 */
.tasks-section {
  flex: 1 1 auto;
}

.tasks-card {
  background: white;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.tasks-list {
  flex: 1 1 auto;
  overflow-y: auto;
  max-height: 520px;
  margin: 20px 0;
  padding-right: 4px;
}

.task-item {
  border: 1px solid #d9e6ff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.18s ease;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.task-item:hover {
  border-color: #9fc1ff;
  background: #f4f9ff;
  transform: translateY(-1px);
  box-shadow: 0 8px 14px rgba(63, 120, 215, 0.1);
}

.task-item.active {
  border-color: #6caaf6;
  background: #eef6ff;
  box-shadow: 0 0 0 2px rgba(108, 170, 246, 0.2);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.task-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.delete-btn {
  width: 24px;
  height: 24px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 14px;
  font-weight: bold;
}

.btn-danger {
  background: linear-gradient(135deg, #dc3545 0%, #c82333 100%);
  color: white;
  border: none;
}

.btn-danger:hover {
  background: linear-gradient(135deg, #c82333 0%, #a71e2a 100%);
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.3);
}

.btn-warning {
  background: linear-gradient(135deg, #f0ad4e 0%, #ec971f 100%);
  color: #fff;
  border: none;
}

.btn-warning:hover {
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(240, 173, 78, 0.3);
}

.btn-secondary {
  background: linear-gradient(135deg, #6c757d 0%, #5a6268 100%);
  color: #fff;
  border: none;
}

.btn-secondary:hover {
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(108, 117, 125, 0.3);
}

.task-status {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
}

.task-status.completed {
  background-color: #d4edda;
  color: #155724;
}

.task-status.pending {
  background-color: #fff3cd;
  color: #856404;
}

.task-status.failed {
  background-color: #f8d7da;
  color: #721c24;
}

.task-status.processing,
.task-status.sent {
  background-color: #d1ecf1;
  color: #0c5460;
}

.task-status.cancelled {
  background-color: #ececec;
  color: #555;
}

.task-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 0.9rem;
  color: #666;
}

.task-time {
  font-family: 'Courier New', monospace;
}

.task-user {
  font-size: 0.85rem;
}

.task-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background-color: #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar.large {
  height: 12px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 0.8rem;
  font-weight: 500;
  color: #666;
  min-width: 40px;
  text-align: right;
}

.empty-tasks {
  text-align: center;
  padding: 60px 20px;
  color: #666;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 20px;
  opacity: 0.5;
}

.empty-tasks p {
  margin-bottom: 20px;
  font-size: 1.1rem;
}

.loading-tasks {
  text-align: center;
  padding: 60px 20px;
  color: #666;
}

.loading-icon {
  font-size: 3rem;
  margin-bottom: 20px;
  animation: spin 2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.task-modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(18, 38, 77, 0.35);
  backdrop-filter: blur(1px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
}

.task-modal {
  width: min(920px, 86vw);
  max-height: 84vh;
  background: #fff;
  border-radius: 16px;
  border: 1px solid #dbe8ff;
  box-shadow: 0 20px 40px rgba(31, 73, 146, 0.25);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.task-modal-header {
  padding: 16px 20px;
  border-bottom: 1px solid #e5efff;
  background: linear-gradient(180deg, #f8fbff 0%, #eef5ff 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-modal-header h3 {
  margin: 0;
  color: #315faa;
  font-size: 1.05rem;
}

.task-modal-body {
  padding: 20px;
  overflow: auto;
}

.task-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.meta-item {
  background: #f8fbff;
  border: 1px solid #deebff;
  border-radius: 10px;
  padding: 12px;
}

.meta-item label,
.task-modal-progress label,
.task-modal-result label {
  display: block;
  font-size: 12px;
  color: #6a86ae;
  margin-bottom: 8px;
}

.task-modal-progress {
  margin-bottom: 16px;
  background: #f8fbff;
  border: 1px solid #deebff;
  border-radius: 10px;
  padding: 12px;
}

.task-modal-result {
  background: #f8fbff;
  border: 1px solid #deebff;
  border-radius: 10px;
  padding: 12px;
}

.result-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.result-title-row label {
  margin-bottom: 0;
}

.result-view-toggle {
  display: inline-flex;
  padding: 3px;
  border: 1px solid #cfe0fb;
  border-radius: 999px;
  background: #eef5ff;
}

.toggle-btn {
  border: 0;
  border-radius: 999px;
  padding: 6px 12px;
  background: transparent;
  color: #4f6e9b;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.toggle-btn.active {
  background: #ffffff;
  color: #2f5fa7;
  box-shadow: 0 1px 4px rgba(47, 95, 167, 0.15);
}

.task-result-loading {
  color: #4f6e9b;
  font-size: 0.9rem;
  padding: 16px 8px;
}

.task-ai-actions {
  margin-top: 14px;
  margin-bottom: 14px;
  display: flex;
  justify-content: flex-start;
  gap: 10px;
  align-items: center;
}

.ai-question-input {
  flex: 1;
  min-width: 280px;
  max-width: 560px;
  height: 38px;
  border: 1px solid #cfe0fb;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
}

.task-ai-result {
  background: #f8fbff;
  border: 1px solid #deebff;
  border-radius: 10px;
  padding: 12px;
}

.ai-analysis-box {
  max-height: 280px;
}

.ai-error {
  color: #b42318;
  background: #fee4e2;
  border: 1px solid #fecdca;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 0.9rem;
}

.task-review-panel {
  margin-top: 14px;
  background: #f8fbff;
  border: 1px solid #deebff;
  border-radius: 10px;
  padding: 12px;
}

.review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.review-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.review-form-full {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-input,
.review-textarea {
  width: 100%;
  border: 1px solid #d5e5ff;
  border-radius: 8px;
  padding: 9px 10px;
  font-size: 13px;
  background: #fff;
}

.review-textarea {
  resize: vertical;
}

.result-data {
  background-color: #f2f7ff;
  border: 1px solid #d5e5ff;
  border-radius: 8px;
  padding: 14px;
  max-height: 360px;
  overflow-y: auto;
  font-family: 'Courier New', monospace;
  font-size: 0.86rem;
}

.result-data pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #333;
}

.structured-result-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.structured-result-card {
  background: #ffffff;
  border: 1px solid #d5e5ff;
  border-radius: 10px;
  padding: 14px;
}

.structured-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.result-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  margin-right: 8px;
  border-radius: 999px;
  background: #e0ecff;
  color: #2f5fa7;
  font-size: 12px;
  font-weight: 700;
}

.result-waybill {
  font-size: 16px;
  font-weight: 700;
  color: #1f3c68;
}

.result-badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.result-badge.success {
  background: #dcfae6;
  color: #067647;
}

.result-badge.warning {
  background: #fef3c7;
  color: #b54708;
}

.result-warning {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff4e5;
  border: 1px solid #f5d8a8;
  color: #9a6700;
  line-height: 1.6;
}

.result-section-card {
  background: #f8fbff;
  border: 1px solid #deebff;
  border-radius: 10px;
  padding: 12px;
}

.result-section-card h4 {
  margin: 0 0 10px;
  font-size: 14px;
  color: #2f5fa7;
}

.result-field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.ocr-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.result-field,
.result-field-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.result-field-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-label {
  font-size: 12px;
  color: #6a86ae;
}

.field-value {
  color: #1f2937;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

@media (max-width: 900px) {
  .result-field-grid,
  .ocr-grid {
    grid-template-columns: 1fr;
  }
}

/* 闁圭顦甸幐鎶藉冀瀹勬壆纭€ */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  gap: 8px;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(102, 126, 234, 0.3);
}

.btn-success {
  background: linear-gradient(135deg, #28a745 0%, #20c997 100%);
  color: white;
}

.btn-success:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(40, 167, 69, 0.3);
}

.btn-outline {
  background: white;
  color: #667eea;
  border: 1px solid #667eea;
}

.btn-outline:hover {
  background: #667eea;
  color: white;
}

.btn-lg {
  padding: 12px 24px;
  font-size: 1.1rem;
}

.btn-sm {
  padding: 4px 12px;
  font-size: 0.8rem;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 闁搞儳鍋撻悥锝夊冀瀹勬壆纭€ */
.icon {
  font-size: 1.2rem;
}

@media (max-width: 1200px) {
  .grid-layout {
    grid-template-columns: 1fr;
  }
  
  .tasks-list {
    max-height: 300px;
  }
}

@media (max-width: 768px) {
  .main-content {
    padding: 0 15px;
  }
  
  .header h1 {
    font-size: 2rem;
  }
  
  .grid-layout {
    gap: 20px;
  }
  
  .upload-area {
    padding: 40px 15px;
  }
  
  .upload-card,
  .result-card,
  .tips-card,
  .tasks-card,
  .data-card {
    padding: 20px;
  }
  
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .header-actions {
    align-self: flex-end;
  }
  
  .result-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
  }
  
  .result-item label {
    width: 100%;
  }
  
  .result-actions {
    flex-direction: column;
  }
  
  .task-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
  }
  
  .task-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
  }
  
  .task-progress {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
    width: 100%;
  }
  
  .progress-bar {
    width: 100%;
  }
  
  .switch-container {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  
  .mode-labels {
    width: 100%;
    justify-content: space-between;
  }
  
  .mode-label {
    flex: 1;
    text-align: center;
  }
}

.data-source-switch {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.local-data-section {
  flex: 0 0 auto;
}

.local-data-card {
  background: white;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.local-search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.local-search-input {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.3s ease;
}

.local-search-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.btn-search-local {
  padding: 12px 20px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-search-local:hover {
  background: #5a6fd6;
  transform: translateY(-1px);
}

.btn-clear {
  padding: 12px 16px;
  background: #e0e0e0;
  color: #666;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-clear:hover {
  background: #d0d0d0;
}

.select-all-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.selected-count.clickable {
  cursor: pointer;
  transition: all 0.2s ease;
}

.selected-count.clickable.has-selection {
  color: #667eea;
  font-weight: 500;
}

.selected-count.clickable.has-selection:hover {
  color: #5a6fd6;
}

.toggle-icon {
  margin-left: 6px;
  font-size: 12px;
}

.selected-panel {
  margin-bottom: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.selected-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.selected-panel-header h4 {
  margin: 0;
  color: white;
  font-size: 16px;
  font-weight: 600;
}

.selected-panel-content {
  max-height: 300px;
  overflow-y: auto;
  padding: 12px;
}

.selected-item-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 8px;
  transition: all 0.2s ease;
}

.selected-item-card:last-child {
  margin-bottom: 0;
}

.selected-item-card:hover {
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.selected-item-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.selected-item-info .item-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selected-item-info .waybill-no {
  font-weight: 600;
  color: #333;
}

.selected-item-info .plate-no {
  font-size: 13px;
  color: #666;
}

.selected-item-info .item-address {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: #888;
}

.selected-item-info .address-item {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.btn-remove {
  width: 28px;
  height: 28px;
  border: none;
  background: #ff4757;
  color: white;
  border-radius: 50%;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-remove:hover {
  background: #ff3344;
  transform: scale(1.1);
}

.btn-danger {
  background: #ff4757 !important;
  color: white !important;
  border: none !important;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-danger:hover {
  background: #ff3344 !important;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
}

.selected-count {
  color: #667eea;
  font-weight: 500;
}

.local-data-list {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
}

.local-data-item {
  display: flex;
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
  cursor: pointer;
  transition: background-color 0.2s;
}

.local-data-item:last-child {
  border-bottom: none;
}

.local-data-item:hover {
  background-color: #f5f7fa;
}

.local-data-item.selected {
  background-color: rgba(102, 126, 234, 0.1);
  border-left: 3px solid #667eea;
}

.item-checkbox {
  margin-right: 16px;
  display: flex;
  align-items: center;
}

.item-checkbox input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
}

.item-content {
  flex: 1;
}

.item-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.waybill-no {
  font-weight: 600;
  color: #333;
  font-size: 1rem;
}

.source-order {
  color: #666;
  font-size: 0.9rem;
}

.item-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.detail-item {
  font-size: 0.85rem;
  color: #555;
}

.detail-item strong {
  color: #333;
}

.item-footer {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  color: #888;
}

.submit-actions {
  margin-top: 20px;
  text-align: center;
}

.empty-local-data,
.loading-local-data {
  text-align: center;
  padding: 60px 20px;
  color: #666;
}

.empty-local-data .hint {
  font-size: 0.9rem;
  color: #999;
  margin-top: 8px;
}
</style>




