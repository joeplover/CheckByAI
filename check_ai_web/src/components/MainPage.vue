<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';

// 初始化路由
const router = useRouter();

// 文件上传相关
const file = ref(null);
const uploading = ref(false);
const uploadMessage = ref('');

// 任务相关
const tasks = ref([]);
const selectedTask = ref(null);
const taskResults = ref([]);
const loadingTasks = ref(false);
const loadingResults = ref(false);

// 模拟任务数据，仅用于初始展示
const mockTasks = [];

// 文件选择处理
const handleFileChange = (event) => {
  file.value = event.target.files[0];
  if (file.value) {
    uploadMessage.value = `已选择文件: ${file.value.name}`;
  } else {
    uploadMessage.value = '';
  }
};

// 上传文件
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

    // 获取本地存储的token
    const token = localStorage.getItem('token');
    
    // 调用后端API上传文件
    const response = await axios.post('http://checkbyai.free.idcfengye.com/api/upload-excel', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'Authorization': token ? `Bearer ${token}` : '',
      },
    });

    if (response.data.success) {
            uploadMessage.value = `上传成功！任务ID: ${response.data.taskId}`;
            // 添加到任务列表
            tasks.value.unshift({
              id: response.data.taskId,
              name: `任务-${response.data.taskId.substring(0, 8)}`,
              status: 'processing',
              createTime: new Date().toLocaleString(),
              taskId: response.data.taskId,
            });
      // 清空文件选择
      file.value = null;
      document.getElementById('file-input').value = '';
    } else {
      uploadMessage.value = `上传失败: ${response.data.error}`;
    }
  } catch (err) {
    console.error('上传失败:', err);
    // 显示更详细的错误信息
    if (err.response) {
      // 服务器返回了响应，但状态码不是2xx
      console.error('响应数据:', err.response.data);
      uploadMessage.value = `上传失败: ${err.response.data.error || err.response.data.message || err.message}`;
    } else {
      // 没有收到服务器响应
      uploadMessage.value = `上传失败: ${err.message}`;
    }
  } finally {
    uploading.value = false;
  }
};

// 获取任务列表
const fetchTasks = async () => {
  loadingTasks.value = true;
  try {
    // 调用后端API获取任务列表
    console.log('获取任务列表');
    const token = localStorage.getItem('token');
    const response = await axios.get('http://checkbyai.free.idcfengye.com/api/tasks', {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    if (response.data.success) {
            // 将后端返回的任务数据转换为前端显示格式
            tasks.value = response.data.tasks.map(task => ({
              id: task.id,
              name: `任务-${task.taskId.substring(0, 8)}`,
              status: task.status,
              createTime: new Date(task.createTime).toLocaleString(),
              // 添加其他需要的字段
              taskId: task.taskId,
              originalTaskId: task.originalTaskId,
              userId: task.userId,
              batchNumber: task.batchNumber,
              totalBatches: task.totalBatches,
              progress: task.progress,
              totalProgress: task.totalProgress
            }));
    } else {
      console.error('获取任务列表失败:', response.data.error);
      tasks.value = [];
    }
  } catch (err) {
    console.error('获取任务列表失败:', err);
    tasks.value = [];
  } finally {
    loadingTasks.value = false;
  }
};

// 获取任务结果
const fetchTaskResults = async (taskId) => {
  loadingResults.value = true;
  selectedTask.value = taskId;
  try {
    // 调用后端API获取任务结果
    console.log('获取任务结果:', taskId);
    const token = localStorage.getItem('token');
    const response = await axios.get(`http://checkbyai.free.idcfengye.com/api/task/${taskId}/results`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });
    
    if (response.data.success) {
      // 将后端返回的结果数据转换为前端显示格式
      taskResults.value = response.data.results.map(result => ({
        id: result.id,
        taskId: result.taskId,
        originalTaskId: result.originalTaskId,
        data: result.data,
        createTime: new Date(result.receiveTime).toLocaleString()
      }));
    } else {
      console.error('获取任务结果失败:', response.data.error);
      taskResults.value = [];
    }
  } catch (err) {
    console.error('获取任务结果失败:', err);
    taskResults.value = [];
  } finally {
    loadingResults.value = false;
  }
};

// 获取当前用户名
const username = ref(localStorage.getItem('username') || '用户');

// 退出登录
const handleLogout = () => {
  // 清除本地存储的token和用户信息
  localStorage.removeItem('token');
  localStorage.removeItem('userId');
  localStorage.removeItem('username');
  localStorage.removeItem('nickname');
  
  // 跳转到登录页
  router.push({ name: 'login' });
};

// 组件挂载时获取任务列表
onMounted(() => {
  fetchTasks();
  username.value = localStorage.getItem('username') || '用户';
});
</script>

<template>
  <div class="main-page">
    <!-- 顶部导航栏 -->
    <div class="top-nav">
      <div class="nav-left">
        <h1 class="system-title">AI Check System</h1>
      </div>
      <div class="nav-right">
        <span class="username">欢迎，{{ username }}</span>
        <button @click="handleLogout" class="logout-btn">退出登录</button>
      </div>
    </div>
    
    <!-- 主体内容区域，分为左侧和右侧 -->
    <div class="content-container">
      <!-- 左侧区域：上传文件和任务列表 -->
      <div class="left-section">
        <div class="upload-section">
          <h2>上传Excel文件</h2>
          <div class="upload-form">
            <div class="file-input-container">
              <input
                type="file"
                id="file-input"
                accept=".xlsx, .xls"
                @change="handleFileChange"
                class="file-input"
              />
              <label for="file-input" class="file-input-label">
                <span v-if="!file">选择Excel文件</span>
                <span v-else>{{ file.name }}</span>
              </label>
            </div>
            <button
              @click="handleUpload"
              class="upload-btn"
              :disabled="uploading"
            >
              {{ uploading ? '上传中...' : '上传' }}
            </button>
          </div>
          <div v-if="uploadMessage" class="upload-message">
            {{ uploadMessage }}
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
                <div class="task-id">{{ task.taskId }}</div>
                <div class="task-name">{{ task.name }}</div>
                <div class="task-status" :class="task.status">
                  {{ task.status === 'COMPLETED' ? '已完成' : task.status === 'processing' ? '处理中' : task.status === 'FAILED' ? '失败' : '未知状态' }}
                </div>
                <div class="task-time">{{ task.createTime }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 右侧区域：结果显示 -->
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
/* 全局样式重置 */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background-color: #f5f7fa;
  color: #333;
  line-height: 1.6;
}

.main-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: 20px;
  gap: 20px;
  background-color: #f5f7fa;
}

/* 顶部导航栏 */
.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #42b883;
  color: white;
  padding: 15px 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  width: 100%;
  height: 60px;
}

.nav-left .system-title {
  font-size: 20px;
  margin: 0;
  font-weight: 600;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.username {
  font-size: 16px;
  font-weight: 500;
}

.logout-btn {
  padding: 8px 16px;
  background-color: #ffffff;
  color: #42b883;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 14px;
  font-weight: 500;
}

.logout-btn:hover {
  background-color: #f0fff8;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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

/* 主体内容区域，分为左侧和右侧 */
.content-container {
  display: flex;
  gap: 20px;
  flex: 1;
  height: calc(100vh - 100px);
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
}

/* 左侧区域：上传文件和任务列表 */
.left-section {
  width: 400px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 右侧区域：结果显示 */
.right-section {
  flex: 1;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 20px;
  height: 100%;
  overflow-y: auto;
}

/* 上传区域 */
.upload-section {
  background-color: #ffffff;
  padding: 24px;
  border-radius: 10px;
  border: 1px solid #e0e0e0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  width: 100%;
  min-height: 180px;
  transition: all 0.3s ease;
}

.upload-section:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
  border-color: #42b883;
}

.upload-form {
  display: flex;
  gap: 15px;
  align-items: center;
  margin-bottom: 15px;
}

.file-input-container {
  position: relative;
  flex: 1;
}

.file-input {
  position: absolute;
  opacity: 0;
  width: 100%;
  height: 100%;
  cursor: pointer;
}

.file-input-label {
  display: inline-block;
  padding: 12px 20px;
  background-color: #42b883;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
  width: 100%;
  text-align: center;
}

.file-input-label:hover {
  background-color: #3aa876;
}

.upload-btn {
  padding: 12px 24px;
  background-color: #4285f4;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
  font-size: 16px;
}

.upload-btn:hover:not(:disabled) {
  background-color: #3367d6;
}

.upload-btn:disabled {
  background-color: #a0c8ff;
  cursor: not-allowed;
}

.upload-message {
  font-size: 14px;
  color: #666;
  margin-top: 10px;
  word-wrap: break-word;
}

/* 任务列表 */
.tasks-section {
  background-color: #ffffff;
  padding: 20px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  width: 100%;
  flex: 1;
  overflow-y: auto;
  min-height: 300px;
}

.tasks-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 15px;
}

.refresh-btn {
  padding: 8px 16px;
  background-color: #4285f4;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
  font-size: 14px;
}

.refresh-btn:hover:not(:disabled) {
  background-color: #3367d6;
}

.refresh-btn:disabled {
  background-color: #a0c8ff;
  cursor: not-allowed;
}

.tasks-list {
  min-height: 200px;
}

.no-tasks {
  text-align: center;
  color: #999;
  padding: 40px 0;
}

.tasks-grid {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.task-item {
  background-color: white;
  padding: 15px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  cursor: pointer;
  transition: all 0.3s;
  width: 100%;
  min-height: 120px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.task-item:hover {
  border-color: #42b883;
  box-shadow: 0 2px 8px rgba(66, 184, 131, 0.15);
}

.task-item.active {
  border-color: #42b883;
  background-color: #f0fff8;
}

.task-id {
  font-size: 12px;
  color: #999;
  margin-bottom: 5px;
  word-break: break-all;
}

.task-name {
  font-weight: 500;
  margin-bottom: 8px;
  color: #333;
  font-size: 16px;
}

.task-status {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 8px;
}

.task-status.COMPLETED {
  background-color: #e6f7e6;
  color: #388e3c;
}

.task-status.PROCESSING,
.task-status.processing {
  background-color: #fff3e0;
  color: #f57c00;
}

.task-status.FAILED {
  background-color: #ffebee;
  color: #d32f2f;
}

.task-time {
  font-size: 12px;
  color: #999;
  text-align: right;
}

/* 结果列表 */
.results-section {
  background-color: #ffffff;
  padding: 20px;
  border-radius: 8px;
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.loading-results {
  text-align: center;
  color: #666;
  padding: 40px 0;
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 16px;
}

.no-results {
  text-align: center;
  color: #999;
  padding: 40px 0;
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 16px;
}

.no-selection {
  background-color: #ffffff;
  border-radius: 8px;
  min-height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  color: #999;
  flex-direction: column;
}

.no-selection-content {
  text-align: center;
  padding: 40px;
}

.no-selection-content h3 {
  color: #666;
  margin-bottom: 10px;
  font-size: 20px;
}

.no-selection-content p {
  color: #999;
  font-size: 16px;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex: 1;
  overflow-y: auto;
}

.result-item {
  background-color: white;
  padding: 24px;
  border-radius: 10px;
  border: 1px solid #e8e8e8;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  overflow-wrap: break-word;
  transition: all 0.3s ease;
}

.result-item:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
  border-color: #42b883;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.result-id {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.result-time {
  font-size: 13px;
  color: #999;
}

.result-data {
  font-size: 15px;
  color: #333;
  line-height: 1.9;
  background-color: #f8f9fa;
  padding: 20px;
  border-radius: 8px;
  white-space: pre-wrap;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  border-left: 4px solid #42b883;
  min-height: 200px;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
}

/* 滚动条样式 */
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

/* 响应式设计 */
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
