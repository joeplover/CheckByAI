<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { API_BASE_URL } from '../config/api.js';

const file = ref(null);
const uploading = ref(false);
const uploadMessage = ref('');

const tasks = ref([]);
const selectedTask = ref(null);
const taskResults = ref([]);
const loadingTasks = ref(false);
const loadingResults = ref(false);

const handleFileChange = (event) => {
  file.value = event.target.files[0];
  if (file.value) {
    uploadMessage.value = `已选择文件: ${file.value.name}`;
  } else {
    uploadMessage.value = '';
  }
};

const handleUpload = async () => {
  if (!file.value) {
    uploadMessage.value = '请选择要上传的Excel文件';
    return;
  }

  uploading.value = true;
  uploadMessage.value = '上传中...';

  try {
    const formData = new FormData();
    formData.append('file', file.value);

    const token = localStorage.getItem('token');
    
    const response = await axios.post(`${API_BASE_URL}/api/upload-excel`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'Authorization': token ? `Bearer ${token}` : '',
      },
    });

    if (response.data.code === 0) {
            uploadMessage.value = `上传成功！任务ID: ${response.data.taskId}`;
            tasks.value.unshift({
              id: response.data.taskId,
              name: `任务-${response.data.taskId.substring(0, 8)}`,
              status: 'processing',
              createTime: new Date().toLocaleString(),
              taskId: response.data.taskId,
            });
      file.value = null;
      document.getElementById('file-input').value = '';
    } else {
      uploadMessage.value = `上传失败: ${response.data.message}`;
    }
  } catch (err) {
    console.error('上传失败:', err);
    if (err.response) {
      console.error('响应数据:', err.response.data);
      uploadMessage.value = `上传失败: ${err.response.data.error || err.response.data.message || err.message}`;
    } else {
      uploadMessage.value = `上传失败: ${err.message}`;
    }
  } finally {
    uploading.value = false;
  }
};

const fetchTasks = async () => {
  loadingTasks.value = true;
  try {
    console.log('获取任务列表');
    const token = localStorage.getItem('token');
    const response = await axios.get(`${API_BASE_URL}/api/tasks`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    if (response.data.code === 0) {
            tasks.value = response.data.tasks.map(task => ({
              id: task.id,
              name: `任务-${task.taskId.substring(0, 8)}`,
              status: task.status,
              createTime: new Date(task.createTime).toLocaleString(),
              taskId: task.taskId,
              originalTaskId: task.originalTaskId,
              userId: task.userId,
              batchNumber: task.batchNumber,
              totalBatches: task.totalBatches,
              progress: task.progress,
              totalProgress: task.totalProgress
            }));
    } else {
      console.error('获取任务列表失败:', response.data.message || '未知错误');
      tasks.value = [];
    }
  } catch (err) {
    console.error('获取任务列表失败:', err);
    tasks.value = [];
  } finally {
    loadingTasks.value = false;
  }
};

const fetchTaskResults = async (taskId) => {
  loadingResults.value = true;
  selectedTask.value = taskId;
  try {
    console.log('获取任务结果:', taskId);
    const token = localStorage.getItem('token');
    const response = await axios.get(`${API_BASE_URL}/api/task/${taskId}/results`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    if (response.data.code === 0) {
      taskResults.value = response.data.results.map(result => ({
        id: result.id,
        taskId: result.taskId,
        originalTaskId: result.originalTaskId,
        data: result.data,
        createTime: new Date(result.receiveTime).toLocaleString()
      }));
    } else {
      console.error('获取任务结果失败:', response.data.message);
      taskResults.value = [];
    }
  } catch (err) {
    console.error('获取任务结果失败:', err);
    taskResults.value = [];
  } finally {
    loadingResults.value = false;
  }
};

onMounted(() => {
  fetchTasks();
});
</script>

<template>
  <div class="main-page">
    <div class="content-container">
      <div class="left-section">
        <div class="upload-section">
          <div class="upload-section-header">
            <h2>上传Excel文件</h2>
            <div class="upload-info">
              <span class="info-icon">ℹ️</span>
              <span>支持 .xlsx 和 .xls 格式文件</span>
            </div>
          </div>
          <div class="upload-form">
            <div 
              class="file-drop-area"
              @dragover.prevent
              @dragenter.prevent
              @drop.prevent="(e) => {
                const droppedFile = e.dataTransfer.files[0];
                if (droppedFile) {
                  file.value = droppedFile;
                  uploadMessage.value = `已选择文件: ${droppedFile.name}`;
                }
              }"
            >
              <div class="file-input-container">
                <input
                  type="file"
                  id="file-input"
                  accept=".xlsx, .xls"
                  @change="handleFileChange"
                  class="file-input"
                />
                <label for="file-input" class="file-input-label">
                  <div class="upload-icon">📁</div>
                  <div class="upload-text">
                    <span v-if="!file" class="primary-text">拖放文件到此处或点击选择</span>
                    <span v-else class="selected-file">{{ file.name }}</span>
                  </div>
                  <div class="upload-hint">
                    <span v-if="!file">支持 .xlsx 和 .xls 格式</span>
                  </div>
                </label>
              </div>
            </div>
            <button
              @click="handleUpload"
              class="upload-btn"
              :disabled="uploading || !file"
            >
              <span class="btn-icon">{{ uploading ? '⏳' : '📤' }}</span>
              <span>{{ uploading ? '上传中...' : '开始上传' }}</span>
            </button>
          </div>
          <div v-if="uploadMessage" class="upload-message" :class="{ success: uploadMessage.includes('成功'), error: uploadMessage.includes('失败') }">
            <span class="message-icon">{{ uploadMessage.includes('成功') ? '✅' : uploadMessage.includes('失败') ? '❌' : 'ℹ️' }}</span>
            <span>{{ uploadMessage }}</span>
          </div>
        </div>

        <div class="tasks-section">
          <h2>任务列表</h2>
          <div class="tasks-header">
            <button @click="fetchTasks" class="refresh-btn" :disabled="loadingTasks">
              {{ loadingTasks ? '刷新中...' : '刷新' }}
            </button>
          </div>
          <div class="tasks-list">
            <div v-if="tasks.length === 0" class="no-tasks">
              暂无任务
            </div>
            <div v-else class="tasks-grid">
              <div
                v-for="task in tasks"
                :key="task.id"
                class="task-item"
                :class="{ active: selectedTask === task.taskId }"
                @click="fetchTaskResults(task.taskId)"
              >
                <div class="task-header">
                  <div class="task-id">{{ task.taskId }}</div>
                  <div class="task-time">{{ task.createTime }}</div>
                </div>
                <div class="task-name">{{ task.name }}</div>
                <div class="task-status" :class="task.status">
                  <span class="status-icon">{{ 
                    task.status === 'COMPLETED' ? '✅' : 
                    task.status === 'processing' ? '⏳' : 
                    task.status === 'FAILED' ? '❌' : 'ℹ️' 
                  }}</span>
                  <span>{{ task.status === 'COMPLETED' ? '已完成' : task.status === 'processing' ? '处理中' : task.status === 'FAILED' ? '失败' : '未知状态' }}</span>
                </div>
                <div v-if="task.totalProgress !== undefined" class="task-progress">
                  <div class="progress-info">
                    <span>进度: {{ task.totalProgress }}%</span>
                    <span v-if="task.batchNumber && task.totalBatches">
                      批次: {{ task.batchNumber }}/{{ task.totalBatches }}
                    </span>
                  </div>
                  <div class="progress-bar">
                    <div class="progress-fill" :style="{ width: task.totalProgress + '%' }"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="right-section">
        <div class="results-section" v-if="selectedTask">
          <h2>任务结果 - {{ selectedTask }}</h2>
          <div v-if="loadingResults" class="loading-results">
            加载结果中...
          </div>
          <div v-else-if="taskResults.length === 0" class="no-results">
            暂无结果
          </div>
          <div v-else class="results-list">
            <div v-for="result in taskResults" :key="result.id" class="result-item">
              <div class="result-header">
                <span class="result-id">结果ID: {{ result.id }}</span>
                <span class="result-time">{{ result.createTime }}</span>
              </div>
              <div class="result-data">
                {{ result.data }}
              </div>
            </div>
          </div>
        </div>
        <div v-else class="no-selection">
          <div class="no-selection-content">
            <h3>请选择一个任务查看结果</h3>
            <p>从左侧任务列表中选择一个任务，查看其详细结果</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

.main-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0;
  background-color: #f5f7fa;
}

h2 {
  color: #333;
  margin-bottom: 20px;
  font-size: 1.4rem;
}

h3 {
  color: #666;
  font-size: 1.2rem;
  margin-bottom: 10px;
}

.content-container {
  display: flex;
  gap: 20px;
  flex: 1;
  height: 100%;
  width: 100%;
}

.left-section {
  width: 400px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.right-section {
  flex: 1;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 20px;
  height: 100%;
  overflow-y: auto;
}

.upload-section {
  background-color: #ffffff;
  padding: 32px;
  border-radius: 16px;
  border: 2px dashed #e3e7ed;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  width: 100%;
  min-height: 240px;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
}

.upload-section:hover {
  box-shadow: 0 6px 24px rgba(74, 108, 247, 0.12);
  border-color: #4a6cf7;
  transform: translateY(-2px);
}

.upload-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.upload-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #f8f9fc;
  border-radius: 20px;
  font-size: 14px;
  color: #6c757d;
}

.info-icon {
  font-size: 16px;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 20px;
}

.file-drop-area {
  border: 2px dashed #e3e7ed;
  border-radius: 12px;
  padding: 40px 24px;
  text-align: center;
  background: #f8f9fc;
  transition: all 0.3s ease;
  cursor: pointer;
}

.file-drop-area:hover {
  border-color: #4a6cf7;
  background: #f0f2ff;
}

.file-input-container {
  position: relative;
  width: 100%;
}

.file-input {
  position: absolute;
  opacity: 0;
  width: 100%;
  height: 100%;
  cursor: pointer;
}

.file-input-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 32px 24px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  width: 100%;
}

.upload-icon {
  font-size: 48px;
  transition: transform 0.3s ease;
}

.file-drop-area:hover .upload-icon {
  transform: scale(1.1);
}

.upload-text {
  text-align: center;
}

.primary-text {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 8px;
}

.selected-file {
  font-size: 16px;
  font-weight: 600;
  color: #4a6cf7;
  background: #f0f2ff;
  padding: 8px 16px;
  border-radius: 8px;
  border: 1px solid #e3e7ed;
}

.upload-hint {
  font-size: 14px;
  color: #6c757d;
  margin-top: 8px;
}

.upload-btn {
  padding: 16px 32px;
  background: linear-gradient(135deg, #4a6cf7, #3c63f5);
  color: white;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(74, 108, 247, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 0 auto;
  width: 240px;
}

.upload-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #3c63f5, #2d56f4);
  box-shadow: 0 6px 20px rgba(74, 108, 247, 0.4);
  transform: translateY(-2px);
}

.upload-btn:disabled {
  background: linear-gradient(135deg, #a0b4ff, #8ba4ff);
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btn-icon {
  font-size: 18px;
}

.upload-message {
  font-size: 14px;
  color: #6c757d;
  margin-top: 16px;
  word-wrap: break-word;
  padding: 12px 16px;
  border-radius: 8px;
  background: #f8f9fc;
  display: flex;
  align-items: center;
  gap: 8px;
  border-left: 4px solid #e3e7ed;
}

.upload-message.success {
  color: #28a745;
  background: #f8fff8;
  border-left-color: #28a745;
}

.upload-message.error {
  color: #dc3545;
  background: #fff8f8;
  border-left-color: #dc3545;
}

.message-icon {
  font-size: 16px;
}

.tasks-section {
  background-color: #ffffff;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid #e3e7ed;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  width: 100%;
  flex: 1;
  overflow-y: auto;
  min-height: 400px;
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
}

.tasks-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e3e7ed;
}

.tasks-header h2 {
  margin: 0;
  color: #2c3e50;
  font-size: 20px;
  font-weight: 600;
}

.refresh-btn {
  padding: 10px 20px;
  background: linear-gradient(135deg, #4a6cf7, #3c63f5);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(74, 108, 247, 0.3);
  display: flex;
  align-items: center;
  gap: 8px;
}

.refresh-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #3c63f5, #2d56f4);
  box-shadow: 0 4px 12px rgba(74, 108, 247, 0.4);
  transform: translateY(-1px);
}

.refresh-btn:disabled {
  background: linear-gradient(135deg, #a0b4ff, #8ba4ff);
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.tasks-list {
  min-height: 240px;
}

.no-tasks {
  text-align: center;
  color: #6c757d;
  padding: 60px 24px;
  background: #f8f9fc;
  border-radius: 12px;
  border: 2px dashed #e3e7ed;
}

.no-tasks::before {
  content: '📋';
  display: block;
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.tasks-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-item {
  background: white;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #e3e7ed;
  cursor: pointer;
  transition: all 0.3s ease;
  width: 100%;
  min-height: 160px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.task-item:hover {
  border-color: #4a6cf7;
  box-shadow: 0 4px 16px rgba(74, 108, 247, 0.15);
  transform: translateY(-2px);
}

.task-item.active {
  border-color: #4a6cf7;
  background-color: #f8f9ff;
  box-shadow: 0 4px 16px rgba(74, 108, 247, 0.15);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 4px;
}

.task-id {
  font-size: 12px;
  color: #6c757d;
  font-weight: 500;
  word-break: break-all;
  padding: 4px 8px;
  background: #f8f9fc;
  border-radius: 6px;
}

.task-time {
  font-size: 12px;
  color: #999;
  font-weight: 500;
  padding: 4px 8px;
  background: #f8f9fc;
  border-radius: 6px;
}

.task-name {
  font-weight: 600;
  color: #2c3e50;
  font-size: 16px;
  line-height: 1.4;
}

.task-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  width: fit-content;
}

.task-status.COMPLETED {
  background: linear-gradient(135deg, #e6f7e6, #d4edda);
  color: #28a745;
  border: 1px solid #c3e6cb;
}

.task-status.PROCESSING,
.task-status.processing {
  background: linear-gradient(135deg, #fff3e0, #fef3cd);
  color: #ffc107;
  border: 1px solid #ffeeba;
}

.task-status.FAILED {
  background: linear-gradient(135deg, #ffebee, #f8d7da);
  color: #dc3545;
  border: 1px solid #f5c6cb;
}

.status-icon {
  font-size: 14px;
}

.task-progress {
  margin-top: 8px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 14px;
  color: #6c757d;
  font-weight: 500;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.1);
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4a6cf7, #3c63f5);
  border-radius: 4px;
  transition: width 0.3s ease;
  box-shadow: 0 0 8px rgba(74, 108, 247, 0.4);
}

.results-section {
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
  padding: 32px;
  border-radius: 16px;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border: 1px solid #e3e7ed;
}

.results-section h2 {
  margin: 0 0 24px 0;
  color: #2c3e50;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 12px;
}

.results-section h2::before {
  content: '📊';
  font-size: 24px;
}

.loading-results {
  text-align: center;
  color: #4a6cf7;
  padding: 80px 24px;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  font-size: 18px;
  font-weight: 500;
  background: #f8f9fc;
  border-radius: 12px;
  border: 2px dashed #e3e7ed;
}

.loading-results::before {
  content: '⏳';
  font-size: 48px;
  margin-bottom: 16px;
  animation: spin 2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.no-results {
  text-align: center;
  color: #6c757d;
  padding: 80px 24px;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  font-size: 18px;
  font-weight: 500;
  background: #f8f9fc;
  border-radius: 12px;
  border: 2px dashed #e3e7ed;
}

.no-results::before {
  content: '📋';
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.no-selection {
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fc 100%);
  border-radius: 16px;
  min-height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  color: #6c757d;
  flex-direction: column;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border: 1px solid #e3e7ed;
}

.no-selection-content {
  text-align: center;
  padding: 60px 40px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  max-width: 400px;
}

.no-selection-content::before {
  content: '👈';
  display: block;
  font-size: 48px;
  margin-bottom: 24px;
  opacity: 0.6;
}

.no-selection-content h3 {
  color: #2c3e50;
  margin-bottom: 16px;
  font-size: 24px;
  font-weight: 600;
}

.no-selection-content p {
  color: #6c757d;
  font-size: 16px;
  line-height: 1.5;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
  flex: 1;
  overflow-y: auto;
}

.result-item {
  background: white;
  padding: 28px;
  border-radius: 16px;
  border: 1px solid #e3e7ed;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  overflow-wrap: break-word;
  transition: all 0.3s ease;
  position: relative;
}

.result-item:hover {
  box-shadow: 0 6px 24px rgba(74, 108, 247, 0.15);
  border-color: #4a6cf7;
  transform: translateY(-2px);
}

.result-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(135deg, #4a6cf7, #3c63f5);
  border-radius: 16px 0 0 16px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e3e7ed;
}

.result-id {
  font-size: 14px;
  color: #4a6cf7;
  font-weight: 600;
  background: #f0f2ff;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid #e3e7ed;
}

.result-time {
  font-size: 14px;
  color: #6c757d;
  font-weight: 500;
  padding: 6px 12px;
  background: #f8f9fc;
  border-radius: 8px;
}

.result-data {
  font-size: 15px;
  color: #2c3e50;
  line-height: 1.8;
  background: linear-gradient(135deg, #f8f9fc 0%, #ffffff 100%);
  padding: 24px;
  border-radius: 12px;
  white-space: pre-wrap;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  border: 1px solid #e3e7ed;
  min-height: 240px;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.05);
}

::-webkit-scrollbar {
  width: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

@media (max-width: 1024px) {
  .content-container {
    flex-direction: column;
  }
  
  .left-section {
    max-width: 100%;
  }
  
  .right-section {
    min-height: 400px;
  }
}
</style>
