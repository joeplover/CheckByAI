<template>
  <div class="file-upload-container">
    <div class="main-content">
      <div class="header">
        <h1>智能文件处理</h1>
        <p class="description">上传 Excel 文件或选择本地数据，选择处理方式进行智能分析</p>
      </div>
      
      <!-- 閺佺増宓侀弶銉︾爱閸掑洦宕?-->
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
      
      <!-- 婢跺嫮鎮婇弬鐟扮础閸掑洦宕插鈧崗?-->
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
        <!-- 瀹革缚鏅堕敍姘瀮娴犳湹绗傛导鐘插隘閸?-->
        <div class="left-section">
          <!-- 鏂囦欢涓婁紶閸栧搫鐓?-->
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
          
          <!-- 鏈湴鏁版嵁閫夋嫨閸栧搫鐓?-->
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
              
              <!-- 鎼滅储妗?-->
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
                        :key="item.id" 
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
                        <button @click="removeSelectedItem(item.id)" class="btn-remove">×</button>
                      </div>
                    </div>
                  </div>
                </transition>
                
                <div class="local-data-list">
                  <div 
                    v-for="item in localDataList" 
                    :key="item.id" 
                    class="local-data-item"
                    :class="{ 'selected': selectedLocalIds.includes(item.id) }"
                    @click="toggleSelectItem(item.id)"
                  >
                    <div class="item-checkbox">
                      <input 
                        type="checkbox" 
                        :checked="selectedLocalIds.includes(item.id)" 
                        @change="toggleSelectItem(item.id)"
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
                  <label>Task ID:</label>
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
        
        <!-- 閸欏厖鏅堕敍姘崲閸斺€虫嫲閺佺増宓佺仦鏇犮仛閸栧搫鐓?-->
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
              <label>Status</label>
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
            <label>处理结果</label>
            <div v-if="taskResultLoading" class="task-result-loading">结果加载中...</div>
            <div v-else class="result-data">
              <pre>{{ decodeUnicode(selectedTask.dataContent || selectedTask.data) || '暂无结果数据' }}</pre>
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

// 閸濆秴绨插蹇旀殶閹?
const isDragover = ref(false);
const selectedFile = ref(null);
const isUploading = ref(false);
const taskId = ref('');
const processTime = ref('');
const fileInput = ref(null);
const useLangChain = ref(false);
const useLocalData = ref(false);

// 閺堫剙婀撮弫鐗堝祦閻╃鍙?
const localDataList = ref([]);
const selectedLocalIds = ref([]);
const selectedItemsMap = ref(new Map());
const selectAllLocal = ref(false);
const isLoadingLocalData = ref(false);
const localSearchKeyword = ref('');
const showSelectedPanel = ref(false);

// 閻╂垵鎯夐張顒€婀撮弫鐗堝祦濡€崇础閸掑洦宕?
watch(useLocalData, (newVal) => {
  if (newVal && useLangChain.value) {
    useLangChain.value = false;
  }
});

// 婢跺嫮鎮婂Ο鈥崇础閸掑洦宕查敍鍫ユЩ濮濄垹婀張顒€婀撮弫鐗堝祦濡€崇础娑撳鍨忛幑銏犲煂LangChain閿?
const handleModeChange = () => {
  if (useLocalData.value && useLangChain.value) {
    useLangChain.value = false;
  }
};

// 娴犺濮熼惄绋垮彠閺佺増宓?
const tasks = ref([]);
const selectedTask = ref(null);
const isLoadingTasks = ref(false);
const taskResultModalVisible = ref(false);
const taskResultLoading = ref(false);
const aiAnalyzing = ref(false);
const aiAnalysis = ref('');
const aiAnalysisError = ref('');
const aiQuestion = ref('');
const taskStatusFilter = ref('ALL');
const TASK_FILTER_OPTIONS = [
  { label: 'All', value: 'ALL' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Processing', value: 'PROCESSING' },
  { label: 'Completed', value: 'COMPLETED' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Cancelled', value: 'CANCELLED' }
];

// 閸旂姾娴囬張顒€婀撮弫鐗堝祦
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
      alert('Login expired, please login again');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.code === 0) {
      localDataList.value = result.data || [];
    } else {
      console.error('鑾峰彇鏈湴鏁版嵁澶辫触:', result.message || '鏈煡閿欒');
      localDataList.value = [];
    }
  } catch (error) {
    console.error('鑾峰彇鏈湴鏁版嵁澶辫触:', error);
    localDataList.value = [];
  } finally {
    isLoadingLocalData.value = false;
  }
};

// 鎼滅储閺堫剙婀撮弫鐗堝祦
const searchLocalData = () => {
  loadLocalData();
};

// 濞撳懘娅庢悳绱?
const clearLocalSearch = () => {
  localSearchKeyword.value = '';
  loadLocalData();
};

// 瀹告煡鈧瀚ㄩ惃鍕殶閹诡喛顕涢幆鍜冪礄閻劋绨ù顔煎З闂堛垺婢橀弰鍓с仛閿?
const selectedItemsData = computed(() => {
  return selectedLocalIds.value.map(id => selectedItemsMap.value.get(id)).filter(Boolean);
});

// 娴犲骸鍑￠柅澶婂灙鐞涖劋鑵戠粔濠氭珟閺屾劙銆?
const removeSelectedItem = (id) => {
  const index = selectedLocalIds.value.indexOf(id);
  if (index > -1) {
    selectedLocalIds.value.splice(index, 1);
    selectedItemsMap.value.delete(id);
  }
};

// 濞撳懐鈹栭幍鈧張澶愨偓澶嬪
const clearAllSelected = () => {
  selectedLocalIds.value = [];
  selectedItemsMap.value.clear();
  selectAllLocal.value = false;
};

// 閸掑洦宕查崗銊┾偓?
const toggleSelectAll = () => {
  if (selectAllLocal.value) {
    localDataList.value.forEach(item => {
      if (!selectedLocalIds.value.includes(item.id)) {
        selectedLocalIds.value.push(item.id);
        selectedItemsMap.value.set(item.id, item);
      }
    });
  } else {
    localDataList.value.forEach(item => {
      selectedItemsMap.value.delete(item.id);
    });
    selectedLocalIds.value = [];
  }
};

// 閸掑洦宕查崡鏇氶嚋闁瀚?
const toggleSelectItem = (id) => {
  const index = selectedLocalIds.value.indexOf(id);
  if (index > -1) {
    selectedLocalIds.value.splice(index, 1);
    selectedItemsMap.value.delete(id);
  } else {
    selectedLocalIds.value.push(id);
    const item = localDataList.value.find(i => i.id === id);
    if (item) {
      selectedItemsMap.value.set(id, item);
    }
  }
  selectAllLocal.value = localDataList.value.every(item => selectedLocalIds.value.includes(item.id));
};

// 閹绘劒姘﹂張顒€婀撮弫鐗堝祦
const submitLocalData = async () => {
  if (selectedLocalIds.value.length === 0) {
    alert('璇烽€夋嫨瑕佹彁浜ょ殑鏁版嵁');
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

    const response = await fetch(buildApiUrl(API_PATHS.SUBMIT_LOCAL_DATA), {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        orderIds: selectedLocalIds.value,
        mode: useLangChain.value ? 'langchain' : 'coze'
      }),
      credentials: 'include'
    });

    if (response.status === 401) {
      alert('Login expired, please login again');
      router.push('/login');
      return;
    }

    const result = await response.json();

    if (result.success || result.code === 0) {
      taskId.value = result.taskId;
      const endTime = new Date();
      const duration = (endTime - startTime) / 1000;
      processTime.value = `${duration.toFixed(2)}s`;
      alert(result.message || '鎻愪氦鎴愬姛');
      await refreshTasks();
      selectedLocalIds.value = [];
      selectAllLocal.value = false;
    } else {
      alert('鎻愪氦澶辫触: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('鎻愪氦澶辫触:', error);
    alert('鎻愪氦澶辫触: ' + error.message);
  } finally {
    isUploading.value = false;
  }
};

// 鐟欙箑褰傞弬鍥︽闁瀚?
const triggerFileInput = () => {
  fileInput.value.click();
};

// 婢跺嫮鎮婇弬鍥︽闁瀚?
const handleFileSelect = (event) => {
  const file = event.target.files[0];
  if (file) {
    selectedFile.value = file;
  }
};

// 婢跺嫮鎮婇弬鍥︽閹锋牗瀚?
const handleFileDrop = (event) => {
  isDragover.value = false;
  const file = event.dataTransfer.files[0];
  if (file && (file.name.endsWith('.xlsx') || file.name.endsWith('.xls'))) {
    selectedFile.value = file;
  }
};

// 娑撳﹣绱堕弬鍥︽
const uploadFile = async () => {
  if (!selectedFile.value) {
    alert('璇峰厛閫夋嫨鏂囦欢');
    return;
  }

  isUploading.value = true;
  const startTime = new Date();

  try {
    const formData = new FormData();
    formData.append('file', selectedFile.value);

    // 閼惧嘲褰噒oken
    const token = localStorage.getItem('token');

    // 閺嬪嫬缂撶拠閿嬬湴婢?
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // 閺嶈宓佸鈧崗宕囧Ц閹線鈧瀚ˋPI缁旑垳鍋?
    const apiPath = useLangChain.value ? API_PATHS.UPLOAD_EXCEL_LANGCHAIN : API_PATHS.UPLOAD_EXCEL;

    const response = await fetch(buildApiUrl(apiPath), {
      method: 'POST',
      body: formData,
      headers: headers,
      credentials: 'include'
    });

    // 濡偓閺屻儱鎼锋惔鏃傚Ц閹?
    if (response.status === 401) {
      // Token鏉╁洦婀￠敍灞惧絹缁€铏规暏閹寸兘鍣搁弬鎵瑜?
      alert('Login expired, please login again');
      // 鐠哄疇娴嗛崚鎵瑜版洟銆?
      router.push('/login');
      return;
    }

    const result = await response.json();

    if (result.success || result.code === 0) {
      taskId.value = result.taskId || result.taskId;
      const endTime = new Date();
      const duration = (endTime - startTime) / 1000;
      processTime.value = `${duration.toFixed(2)}s`;
      alert('File processed successfully');
      // 娑撳﹣绱堕幋鎰閸氬骸鍩涢弬棰佹崲閸斺€冲灙鐞?
      await refreshTasks();
    } else {
      alert('澶勭悊澶辫触: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('涓婁紶澶辫触:', error);
    alert('涓婁紶澶辫触: ' + error.message);
  } finally {
    isUploading.value = false;
  }
};

// 澶嶅埗浠诲姟ID
const copyTaskId = () => {
  if (taskId.value) {
    navigator.clipboard.writeText(taskId.value).then(() => {
      alert('Task ID copied');
    });
  }
};

// 闁插秶鐤嗘稉濠佺炊
const resetUpload = () => {
  selectedFile.value = null;
  taskId.value = '';
  processTime.value = '';
  fileInput.value.value = '';
};

// 閼惧嘲褰囨禒璇插閸掓銆?
const refreshTasks = async () => {
  isLoadingTasks.value = true;
  try {
    // 閼惧嘲褰噒oken
    const token = localStorage.getItem('token');

    // 閺嬪嫬缂撶拠閿嬬湴婢?
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

    // 濡偓閺屻儱鎼锋惔鏃傚Ц閹?
    if (response.status === 401) {
      // Token鏉╁洦婀￠敍灞惧絹缁€铏规暏閹寸兘鍣搁弬鎵瑜?
      alert('Login expired, please login again');
      // 鐠哄疇娴嗛崚鎵瑜版洟銆?
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
      console.error('鑾峰彇浠诲姟鍒楄〃澶辫触:', result.error || result.message);
      tasks.value = [];
    }
  } catch (error) {
    console.error('鑾峰彇浠诲姟鍒楄〃澶辫触:', error);
    tasks.value = [];
  } finally {
    isLoadingTasks.value = false;
  }
};

// 閹垫挸绱戞禒璇插缂佹挻鐏夊鍦崶
const openTaskModal = async (task) => {
  selectedTask.value = { ...task };
  taskResultModalVisible.value = true;
  taskResultLoading.value = true;
  aiAnalyzing.value = false;
  aiAnalysis.value = '';
  aiAnalysisError.value = '';
  aiQuestion.value = '';
  const resultData = await fetchTaskResults(task.taskId);
  if (selectedTask.value && selectedTask.value.taskId === task.taskId && resultData !== null) {
    selectedTask.value.dataContent = resultData;
  }
  taskResultLoading.value = false;
};

// 閼惧嘲褰囨禒璇插缂佹挻鐏?
const fetchTaskResults = async (taskId) => {
  try {
    // 閼惧嘲褰噒oken
    const token = localStorage.getItem('token');

    // 閺嬪嫬缂撶拠閿嬬湴婢?
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(buildApiUrl(API_PATHS.GET_TASK_RESULTS.replace(':taskId', taskId)), {
      method: 'GET',
      headers: headers,
      credentials: 'include'
    });

    // 濡偓閺屻儱鎼锋惔鏃傚Ц閹?
    if (response.status === 401) {
      // Token鏉╁洦婀￠敍灞惧絹缁€铏规暏閹寸兘鍣搁弬鎵瑜?
      alert('Login expired, please login again');
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
      console.error('鑾峰彇浠诲姟缁撴灉澶辫触:', result.error || result.message);
      return null;
    }
  } catch (error) {
    console.error('鑾峰彇浠诲姟缁撴灉澶辫触:', error);
    return null;
  }
};

// 閸掔娀娅庢禒璇插
const deleteTask = async (taskId) => {
  if (!confirm('Confirm delete this task?')) {
    return;
  }

  try {
    // 閼惧嘲褰噒oken
    const token = localStorage.getItem('token');

    // 閺嬪嫬缂撶拠閿嬬湴婢?
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // 鐠嬪啰鏁ら崚鐘绘珟娴犺濮熼惃鍑橮I
    // 濞夈劍鍓伴敍姘倵缁旑垱甯撮崣?/api/task/{taskId} 鏉╂瑩鍣烽惃?taskId 閺勵垯绗熼崝鈥崇摟濞?task.task_id閿涘牅绗夐弰顖涙殶閹诡喖绨辨稉濠氭暛id閿?
    const response = await fetch(buildApiUrl(`/api/task/${taskId}`), {
      method: 'DELETE',
      headers: headers,
      credentials: 'include'
    });

    // 濡偓閺屻儱鎼锋惔鏃傚Ц閹?
    if (response.status === 401) {
      // Token鏉╁洦婀￠敍灞惧絹缁€铏规暏閹寸兘鍣搁弬鎵瑜?
      alert('Login expired, please login again');
      // 鐠哄疇娴嗛崚鎵瑜版洟銆?
      router.push('/login');
      return;
    }

    const result = await response.json();

    if (result.success || result.status === 'success') {
      alert('Task deleted successfully');
      // 鍒锋柊浠诲姟鍒楄〃
      await refreshTasks();
      // 婵″倹鐏夐崚鐘绘珟閻ㄥ嫭妲歌ぐ鎾冲闁鑵戦惃鍕崲閸斺槄绱濆〒鍛存珟闁鑵戦悩鑸碘偓?
      if (selectedTask.value && selectedTask.value.taskId === taskId) {
        selectedTask.value = null;
      }
    } else {
      alert('鍒犻櫎澶辫触: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('鍒犻櫎浠诲姟澶辫触:', error);
    alert('鍒犻櫎澶辫触: ' + error.message);
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
  if (!confirm('纭畾Cancel杩欎釜浠诲姟鍚楋紵')) {
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
      alert('Login expired, please login again');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.success) {
      alert('Task cancelled');
      await refreshTasks();
    } else {
      alert('Cancel澶辫触: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('Cancel浠诲姟澶辫触:', error);
    alert('Cancel澶辫触: ' + error.message);
  }
};

const retryTask = async (taskId) => {
  if (!confirm('纭畾Retry杩欎釜浠诲姟鍚楋紵')) {
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
      alert('Login expired, please login again');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.success) {
      alert(`Retry宸叉彁浜わ紝鏂颁换鍔D: ${result.newTaskId}`);
      await refreshTasks();
    } else {
      alert('Retry澶辫触: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('Retry浠诲姟澶辫触:', error);
    alert('Retry澶辫触: ' + error.message);
  }
};

const askAiForTaskAnalysis = async () => {
  if (!selectedTask.value) {
    return;
  }

  const resultText = decodeUnicode(selectedTask.value.dataContent || selectedTask.value.data || '');
  if (!resultText) {
    aiAnalysisError.value = '褰撳墠浠诲姟鏆傛棤鍙垎鏋愮殑缁撴灉鏁版嵁';
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
      ? `璇峰熀浜庝互涓嬩换鍔″鐞嗙粨鏋滐紝閲嶇偣鍥炵瓟杩欎釜闂锛?{customQuestion}`
      : '璇峰浠ヤ笅浠诲姟澶勭悊缁撴灉杩涜鏁版嵁鍒嗘瀽锛屽苟鎸夆€滃叧閿粨璁恒€佸紓甯哥偣銆侀闄╂彁绀恒€佷紭鍖栧缓璁€濆洓閮ㄥ垎杈撳嚭';
    const prompt = `${instruction}\n\n浠诲姟ID锛?{selectedTask.value.taskId}\n\n澶勭悊缁撴灉锛歕n${resultText.slice(0, 12000)}`;

    const response = await fetch(buildApiUrl(API_PATHS.AI_CHAT), {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify({ message: prompt })
    });

    if (response.status === 401) {
      alert('Login expired, please login again');
      router.push('/login');
      return;
    }

    const result = await response.json();
    if (result.code === 0 && result.data && result.data.response) {
      aiAnalysis.value = result.data.response;
    } else {
      aiAnalysisError.value = result.message || 'AI 鍒嗘瀽澶辫触锛岃绋嶅悗Retry';
    }
  } catch (error) {
    aiAnalysisError.value = `AI 鍒嗘瀽澶辫触: ${error.message}`;
  } finally {
    aiAnalyzing.value = false;
  }
};

// 鍏抽棴娴犺濮熺紒鎾寸亯瀵湱鐛?
const closeTaskModal = () => {
  taskResultModalVisible.value = false;
  taskResultLoading.value = false;
  aiAnalyzing.value = false;
  aiAnalysis.value = '';
  aiAnalysisError.value = '';
  aiQuestion.value = '';
  selectedTask.value = null;
};

// 閺嶇厧绱￠崠鏍ㄦ）閺?
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

// 鐟欙絿鐖淯nicode鏉烆兛绠熸惔蹇撳灙
const decodeUnicode = (str) => {
  if (!str) return '';
  try {
    // 鐟欙絿鐖淯nicode鏉烆兛绠熸惔蹇撳灙
    return decodeURIComponent(JSON.parse('"' + str.replace(/"/g, '\\"') + '"'));
  } catch (error) {
    // 婵″倹鐏夌憴锝囩垳婢惰精瑙﹂敍宀冪箲閸ョ偛甯慨瀣摟缁楋缚瑕?
    return str;
  }
};

// 缂佸嫪娆㈤幐鍌濇祰閺冩儼骞忛崣鏍︽崲閸斺€冲灙鐞?
onMounted(() => {
  refreshTasks();
});

// 閻╂垵鎯夐張顒€婀撮弫鐗堝祦濡€崇础閸掑洦宕?
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

/* 婢跺嫮鎮婇弬鐟扮础閸掑洦宕插鈧崗?*/
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

/* 缂冩垶鐗哥敮鍐ㄧ湰 */
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

/* 娴犺濮熼崚妤勩€冮弽宄扮础 */
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

/* 閺佺増宓佺仦鏇犮仛閸栧搫鐓?*/
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

/* 閹稿鎸抽弽宄扮础 */
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

/* 閸ョ偓鐖ｉ弽宄扮础 */
.icon {
  font-size: 1.2rem;
}

/* 閸濆秴绨插蹇氼啎鐠?*/
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

/* 閺佺増宓侀弶銉︾爱閸掑洦宕插鈧崗?*/
.data-source-switch {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

/* 鏈湴鏁版嵁閫夋嫨閸栧搫鐓?*/
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




