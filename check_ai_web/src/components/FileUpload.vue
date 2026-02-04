<template>
  <div class="file-upload-container">
    <div class="main-content">
      <div class="header">
        <h1>智能文件处理</h1>
        <p class="description">上传Excel文件，选择处理方式进行智能分析</p>
      </div>
      
      <!-- 处理方式切换开关 -->
      <div class="processing-mode-switch">
        <div class="switch-container">
          <label class="switch-label">
            <input type="checkbox" v-model="useLangChain" class="switch-input">
            <span class="switch-slider"></span>
          </label>
          <div class="mode-labels">
            <span :class="['mode-label', { active: !useLangChain }]">Coze工作流</span>
            <span :class="['mode-label', { active: useLangChain }]">LangChain Agent</span>
          </div>
        </div>
        <div class="mode-description">
          <p v-if="!useLangChain">使用Coze工作流进行文件处理，适合标准任务流程</p>
          <p v-else>使用LangChain Agent进行智能分析，适合复杂任务处理</p>
        </div>
      </div>
      
      <div class="grid-layout">
        <!-- 左侧：文件上传区域 -->
        <div class="left-section">
          <div class="upload-section">
            <div class="upload-card">
              <div class="card-header">
                <h2>文件上传</h2>
                <p>支持.xlsx和.xls格式文件，最大10MB</p>
              </div>
              
              <div class="upload-area" :class="{ 'dragover': isDragover }" 
                   @dragover.prevent="isDragover = true"
                   @dragleave.prevent="isDragover = false"
                   @drop.prevent="handleFileDrop">
                <input type="file" ref="fileInput" @change="handleFileSelect" 
                   accept=".xlsx,.xls" style="display: none;">
                <div class="upload-icon">📁</div>
                <h3>{{ selectedFile ? selectedFile.name : '点击或拖拽文件到此处上传' }}</h3>
                <p>{{ selectedFile ? '已选择文件: ' + selectedFile.name : '支持.xlsx和.xls格式' }}</p>
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
                  <span class="icon">🔄</span> 上传新文件
                </button>
                <button @click="refreshTasks" class="btn btn-primary">
                  <span class="icon">🔄</span> 刷新任务列表
                </button>
              </div>
            </div>
          </div>
          
          <div class="tips-section">
            <div class="tips-card">
              <h3>📝 使用提示</h3>
              <ul>
                <li>请确保Excel文件格式正确，包含必要的列信息</li>
                <li>文件大小不要超过10MB，否则可能上传失败</li>
                <li>处理时间取决于文件大小和网络速度，请耐心等待</li>
                <li>处理完成后，结果将自动保存到系统中</li>
              </ul>
            </div>
          </div>
        </div>
        
        <!-- 右侧：任务和数据展示区域 -->
        <div class="right-section">
          <div class="tasks-section">
            <div class="tasks-card">
              <div class="card-header">
                <h2>任务历史记录</h2>
                <div class="header-actions">
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
                  :class="{ 'active': selectedTask && selectedTask.id === task.id }"
                >
                  <div class="task-header">
                    <span class="task-id-small">{{ task.taskId }}</span>
                    <div class="task-header-actions">
                      <span :class="['task-status', task.status.toLowerCase()]">{{ task.status }}</span>
                      <button @click.stop="deleteTask(task.id, task.taskId)" class="btn btn-sm btn-danger delete-btn">
                        <span class="icon">×</span>
                      </button>
                    </div>
                  </div>
                  <div class="task-info" @click="selectTask(task)">
                    <span class="task-time">{{ formatDate(task.createTime) }}</span>
                    <span class="task-user">用户: {{ task.userId }}</span>
                  </div>
                  <div class="task-progress" @click="selectTask(task)">
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
          
          <div class="data-section" v-if="selectedTask">
            <div class="data-card">
              <div class="card-header">
                <h2>任务详情</h2>
                <button @click="clearSelectedTask" class="btn btn-sm btn-outline">
                  <span class="icon">×</span> 关闭
                </button>
              </div>
              <div class="data-content">
                <div class="data-item">
                  <label>任务ID:</label>
                  <span class="task-id">{{ selectedTask.taskId }}</span>
                </div>
                <div class="data-item">
                  <label>创建时间:</label>
                  <span>{{ formatDate(selectedTask.createTime) }}</span>
                </div>
                <div class="data-item">
                  <label>更新时间:</label>
                  <span>{{ formatDate(selectedTask.updateTime) }}</span>
                </div>
                <div class="data-item">
                  <label>用户ID:</label>
                  <span>{{ selectedTask.userId }}</span>
                </div>
                <div class="data-item">
                  <label>状态:</label>
                  <span :class="['status', selectedTask.status.toLowerCase()]">{{ selectedTask.status }}</span>
                </div>
                <div class="data-item">
                  <label>进度:</label>
                  <div class="progress-bar large">
                    <div class="progress-fill" :style="{ width: selectedTask.progress + '%' }"></div>
                  </div>
                  <span class="progress-text">{{ selectedTask.progress }}%</span>
                </div>
                <div class="data-item">
                  <label>处理结果:</label>
                  <div class="result-data">
                    <pre>{{ decodeUnicode(selectedTask.dataContent || selectedTask.data) }}</pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { API_PATHS, buildApiUrl } from '../config/api';

// 响应式数据
const isDragover = ref(false);
const selectedFile = ref(null);
const isUploading = ref(false);
const taskId = ref('');
const processTime = ref('');
const fileInput = ref(null);
const useLangChain = ref(false); // 切换开关状态

// 任务相关数据
const tasks = ref([]);
const selectedTask = ref(null);
const isLoadingTasks = ref(false);

// 触发文件选择
const triggerFileInput = () => {
  fileInput.value.click();
};

// 处理文件选择
const handleFileSelect = (event) => {
  const file = event.target.files[0];
  if (file) {
    selectedFile.value = file;
  }
};

// 处理文件拖拽
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
    alert('请先选择文件');
    return;
  }

  isUploading.value = true;
  const startTime = new Date();

  try {
    const formData = new FormData();
    formData.append('file', selectedFile.value);

    // 获取token
    const token = localStorage.getItem('token');

    // 构建请求头
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // 根据开关状态选择API端点
    const apiPath = useLangChain.value ? API_PATHS.UPLOAD_EXCEL_LANGCHAIN : API_PATHS.UPLOAD_EXCEL;

    const response = await fetch(buildApiUrl(apiPath), {
      method: 'POST',
      body: formData,
      headers: headers,
      credentials: 'include'
    });

    const result = await response.json();

    if (result.success || result.code === 0) {
      taskId.value = result.taskId || result.taskId;
      const endTime = new Date();
      const duration = (endTime - startTime) / 1000;
      processTime.value = `${duration.toFixed(2)}秒`;
      alert('文件处理成功！');
      // 上传成功后刷新任务列表
      await refreshTasks();
    } else {
      alert('处理失败: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('上传失败:', error);
    alert('上传失败: ' + error.message);
  } finally {
    isUploading.value = false;
  }
};

// 复制任务ID
const copyTaskId = () => {
  if (taskId.value) {
    navigator.clipboard.writeText(taskId.value).then(() => {
      alert('任务ID已复制到剪贴板');
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

// 获取任务列表
const refreshTasks = async () => {
  isLoadingTasks.value = true;
  try {
    // 获取token
    const token = localStorage.getItem('token');

    // 构建请求头
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(buildApiUrl(API_PATHS.GET_TASKS), {
      method: 'GET',
      headers: headers,
      credentials: 'include'
    });

    const result = await response.json();

    if (result.success || result.code === 0) {
      tasks.value = result.tasks || [];
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

// 选择任务
const selectTask = async (task) => {
  selectedTask.value = task;
  // 获取任务结果
  await fetchTaskResults(task.taskId);
};

// 获取任务结果
const fetchTaskResults = async (taskId) => {
  try {
    // 获取token
    const token = localStorage.getItem('token');

    // 构建请求头
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(buildApiUrl(API_PATHS.GET_TASK_RESULTS.replace(':taskId', taskId)), {
      method: 'GET',
      headers: headers,
      credentials: 'include'
    });

    const result = await response.json();

    if (result.success || result.code === 0) {
      const results = result.results || [];
      if (results.length > 0) {
        // 将第一个结果的data赋值给selectedTask.dataContent
        selectedTask.value.dataContent = results[0].data;
      }
    } else {
      console.error('获取任务结果失败:', result.error || result.message);
    }
  } catch (error) {
    console.error('获取任务结果失败:', error);
  }
};

// 删除任务
const deleteTask = async (taskId, taskTaskId) => {
  if (!confirm('确定要删除这个任务吗？')) {
    return;
  }

  try {
    // 获取token
    const token = localStorage.getItem('token');

    // 构建请求头
    const headers = {
      'Content-Type': 'application/json'
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // 调用删除任务的API
    const response = await fetch(buildApiUrl(`/api/task/${taskId}`), {
      method: 'DELETE',
      headers: headers,
      credentials: 'include'
    });

    const result = await response.json();

    if (result.success || result.status === 'success') {
      alert('任务删除成功！');
      // 刷新任务列表
      await refreshTasks();
      // 如果删除的是当前选中的任务，清除选中状态
      if (selectedTask.value && selectedTask.value.id === taskId) {
        selectedTask.value = null;
      }
    } else {
      alert('删除失败: ' + (result.error || result.message));
    }
  } catch (error) {
    console.error('删除任务失败:', error);
    alert('删除失败: ' + error.message);
  }
};

// 清除选中的任务
const clearSelectedTask = () => {
  selectedTask.value = null;
};

// 格式化日期
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

// 解码Unicode转义序列
const decodeUnicode = (str) => {
  if (!str) return '';
  try {
    // 解码Unicode转义序列
    return decodeURIComponent(JSON.parse('"' + str.replace(/"/g, '\\"') + '"'));
  } catch (error) {
    // 如果解码失败，返回原始字符串
    return str;
  }
};

// 组件挂载时获取任务列表
onMounted(() => {
  refreshTasks();
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

/* 处理方式切换开关 */
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

/* 网格布局 */
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

/* 任务列表样式 */
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
  max-height: 400px;
  margin: 20px 0;
}

.task-item {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  background-color: #f9f9f9;
}

.task-item:hover {
  border-color: #667eea;
  background-color: #f0f0ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.1);
}

.task-item.active {
  border-color: #667eea;
  background-color: rgba(102, 126, 234, 0.05);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
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

.task-status {
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 0.8rem;
  font-weight: 500;
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

/* 数据展示区域 */
.data-section {
  flex: 1 1 auto;
}

.data-card {
  background: white;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.data-content {
  flex: 1 1 auto;
  margin: 20px 0;
  overflow-y: auto;
}

.data-item {
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.data-item label {
  display: block;
  font-weight: 500;
  color: #555;
  margin-bottom: 8px;
  font-size: 0.9rem;
}

.result-data {
  background-color: #f0f0f0;
  border-radius: 6px;
  padding: 15px;
  max-height: 300px;
  overflow-y: auto;
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
}

.result-data pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #333;
}

/* 按钮样式 */
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

/* 图标样式 */
.icon {
  font-size: 1.2rem;
}

/* 响应式设计 */
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
</style>