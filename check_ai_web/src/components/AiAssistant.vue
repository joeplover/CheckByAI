<template>
  <div class="ai-assistant-container">
    <div class="assistant-header">
      <h1>AI 智能问答助手</h1>
      <p class="subtitle">支持文档上传、RAG检索、智能对话</p>
    </div>

    <div class="main-content">
      <!-- 左侧：文档管理 -->
      <div class="sidebar">
        <div class="sidebar-card">
          <h3>文档管理</h3>
          <div class="upload-area" @click="triggerFileUpload" @dragover.prevent @drop.prevent="handleFileDrop">
            <input type="file" ref="fileInput" @change="handleFileSelect" accept=".txt,.pdf,.doc,.docx,.md" style="display: none;">
            <div class="upload-icon">📄</div>
            <p>点击或拖拽上传文档</p>
            <p class="hint">支持 TXT, PDF, DOC, DOCX, MD</p>
          </div>
          
          <div class="document-list" v-if="uploadedDocuments.length > 0">
            <h4>已上传文档</h4>
            <div class="doc-item" v-for="doc in uploadedDocuments" :key="doc.id">
              <span class="doc-icon">📄</span>
              <span class="doc-name">{{ doc.name }}</span>
              <span class="doc-segments">{{ doc.segments }} 片段</span>
            </div>
          </div>
        </div>

        <div class="sidebar-card">
          <h3>对话设置</h3>
          <div class="setting-item">
            <label class="checkbox-label">
              <input type="checkbox" v-model="useRag">
              <span>启用 RAG 检索</span>
            </label>
            <p class="setting-hint">基于上传的文档进行回答</p>
          </div>
        </div>

        <div class="sidebar-card">
          <button @click="clearHistory" class="btn btn-danger-outline btn-block">
            清除对话历史
          </button>
        </div>
      </div>

      <!-- 右侧：对话区域 -->
      <div class="chat-area">
        <div class="messages-container" ref="messagesContainer">
          <div class="message" v-for="(msg, index) in messages" :key="index" :class="msg.role">
            <div class="message-avatar">
              <span v-if="msg.role === 'user'">👤</span>
              <span v-else>🤖</span>
            </div>
            <div class="message-content">
              <div class="message-text" v-html="formatMessage(msg.content)"></div>
              <div class="message-time">{{ msg.time }}</div>
            </div>
          </div>
          
          <div class="message assistant" v-if="isTyping">
            <div class="message-avatar">🤖</div>
            <div class="message-content">
              <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </div>

        <div class="input-area">
          <div class="input-wrapper">
            <textarea 
              v-model="userInput" 
              @keydown.enter.exact.prevent="sendMessage"
              placeholder="输入您的问题..."
              rows="1"
              ref="inputTextarea"
            ></textarea>
            <button @click="sendMessage" class="btn btn-primary" :disabled="!userInput.trim() || isTyping">
              <span v-if="isTyping">处理中...</span>
              <span v-else>发送</span>
            </button>
          </div>
          <div class="input-hint">
            按 Enter 发送消息，Shift + Enter 换行
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue';
import { API_BASE_URL, API_PATHS, buildApiUrl } from '../config/api';
import { useRouter } from 'vue-router';

const router = useRouter();

const messages = ref([]);
const userInput = ref('');
const isTyping = ref(false);
const useRag = ref(false);
const messagesContainer = ref(null);
const inputTextarea = ref(null);
const fileInput = ref(null);
const uploadedDocuments = ref([]);

const triggerFileUpload = () => {
  fileInput.value.click();
};

const handleFileSelect = async (event) => {
  const file = event.target.files[0];
  if (file) {
    await uploadDocument(file);
  }
};

const handleFileDrop = async (event) => {
  const file = event.dataTransfer.files[0];
  if (file) {
    await uploadDocument(file);
  }
};

const uploadDocument = async (file) => {
  const token = localStorage.getItem('token');
  const formData = new FormData();
  formData.append('file', file);

  try {
    const response = await fetch(buildApiUrl(API_PATHS.AI_DOCUMENT_UPLOAD), {
      method: 'POST',
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: formData
    });

    const result = await response.json();
    
    if (result.code === 0) {
      uploadedDocuments.value.push({
        id: Date.now(),
        name: file.name,
        segments: result.data.segments
      });
      addMessage('assistant', `文档 "${file.name}" 上传成功，已分割为 ${result.data.segments} 个片段。`);
    } else {
      addMessage('assistant', `文档上传失败: ${result.message}`);
    }
  } catch (error) {
    addMessage('assistant', `文档上传失败: ${error.message}`);
  }
  
  fileInput.value.value = '';
};

const sendMessage = async () => {
  const message = userInput.value.trim();
  if (!message || isTyping.value) return;

  addMessage('user', message);
  userInput.value = '';
  isTyping.value = true;

  try {
    const token = localStorage.getItem('token');
    const apiPath = useRag.value ? API_PATHS.AI_CHAT_RAG : API_PATHS.AI_CHAT;
    
    const response = await fetch(buildApiUrl(apiPath), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify({ message })
    });

    const result = await response.json();
    
    if (result.code === 0) {
      addMessage('assistant', result.data.response);
    } else {
      addMessage('assistant', `错误: ${result.message}`);
    }
  } catch (error) {
    addMessage('assistant', `请求失败: ${error.message}`);
  } finally {
    isTyping.value = false;
  }
};

const addMessage = (role, content) => {
  messages.value.push({
    role,
    content,
    time: new Date().toLocaleTimeString()
  });
  scrollToBottom();
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

const clearHistory = async () => {
  const token = localStorage.getItem('token');
  
  try {
    const response = await fetch(buildApiUrl(API_PATHS.AI_HISTORY), {
      method: 'DELETE',
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    });

    const result = await response.json();
    
    if (result.code === 0) {
      messages.value = [];
      addMessage('assistant', '对话历史已清除。');
    }
  } catch (error) {
    console.error('清除历史失败:', error);
  }
};

const formatMessage = (content) => {
  if (!content) return '';
  return content
    .replace(/\n/g, '<br>')
    .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>');
};

onMounted(() => {
  addMessage('assistant', '您好！我是AI智能问答助手。您可以上传文档后启用RAG检索，我会基于文档内容回答您的问题。');
});
</script>

<style scoped>
.ai-assistant-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.assistant-header {
  text-align: center;
  margin-bottom: 30px;
}

.assistant-header h1 {
  font-size: 2rem;
  color: #333;
  margin-bottom: 10px;
}

.subtitle {
  color: #666;
  font-size: 1rem;
}

.main-content {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 20px;
  height: calc(100vh - 200px);
  min-height: 600px;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.sidebar-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.sidebar-card h3 {
  font-size: 1rem;
  color: #333;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.upload-area {
  border: 2px dashed #ddd;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
}

.upload-area:hover {
  border-color: #667eea;
  background: #f8f9ff;
}

.upload-icon {
  font-size: 2rem;
  margin-bottom: 10px;
}

.upload-area p {
  margin: 5px 0;
  color: #666;
}

.upload-area .hint {
  font-size: 0.85rem;
  color: #999;
}

.document-list {
  margin-top: 15px;
}

.document-list h4 {
  font-size: 0.9rem;
  color: #666;
  margin-bottom: 10px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 6px;
  margin-bottom: 8px;
}

.doc-icon {
  font-size: 1.2rem;
}

.doc-name {
  flex: 1;
  font-size: 0.9rem;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-segments {
  font-size: 0.8rem;
  color: #667eea;
  background: #eef0ff;
  padding: 2px 8px;
  border-radius: 10px;
}

.setting-item {
  margin-bottom: 15px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: #667eea;
}

.setting-hint {
  font-size: 0.85rem;
  color: #999;
  margin-top: 5px;
  margin-left: 28px;
}

.btn-block {
  width: 100%;
}

.btn-danger-outline {
  background: transparent;
  color: #ff4757;
  border: 1px solid #ff4757;
}

.btn-danger-outline:hover {
  background: #ff4757;
  color: white;
}

.chat-area {
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: #667eea;
}

.message-content {
  max-width: 70%;
}

.message-text {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.message.user .message-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-text {
  background: #f0f0f0;
  color: #333;
  border-bottom-left-radius: 4px;
}

.message-text :deep(pre) {
  background: #2d2d2d;
  color: #f8f8f2;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 10px 0;
}

.message-text :deep(code) {
  background: #e8e8e8;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.message.user .message-text :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}

.message-time {
  font-size: 0.75rem;
  color: #999;
  margin-top: 5px;
  text-align: right;
}

.message.user .message-time {
  text-align: left;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
  background: #f0f0f0;
  border-radius: 12px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.input-area {
  padding: 20px;
  border-top: 1px solid #eee;
  background: #fafafa;
}

.input-wrapper {
  display: flex;
  gap: 12px;
}

.input-wrapper textarea {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 1rem;
  resize: none;
  max-height: 120px;
  transition: border-color 0.3s ease;
}

.input-wrapper textarea:focus {
  outline: none;
  border-color: #667eea;
}

.input-wrapper .btn {
  padding: 12px 24px;
}

.input-hint {
  font-size: 0.8rem;
  color: #999;
  margin-top: 8px;
  text-align: center;
}

@media (max-width: 768px) {
  .main-content {
    grid-template-columns: 1fr;
    height: auto;
  }
  
  .sidebar {
    order: 2;
  }
  
  .chat-area {
    order: 1;
    height: 500px;
  }
}
</style>
