<script setup>
import { ref } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';
import { API_PATHS, buildApiUrl } from '../config/api';

const router = useRouter();

const username = ref('');
const password = ref('');
const confirmPassword = ref('');
const nickname = ref('');
const email = ref('');
const phone = ref('');
const error = ref('');
const loading = ref(false);

const handleRegister = async () => {
  // 表单验证
  if (!username.value || !password.value || !confirmPassword.value) {
    error.value = '请填写必填字段';
    return;
  }

  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致';
    return;
  }

  if (password.value.length < 6) {
    error.value = '密码长度不能少于6位';
    return;
  }

  loading.value = true;
  error.value = '';

  try {
    // 调用后端的注册API
    const response = await axios.post(buildApiUrl(API_PATHS.REGISTER), {
      username: username.value,
      password: password.value,
      nickname: nickname.value,
      email: email.value,
      phone: phone.value
    });
    
    if (response.data.success) {
      // 注册成功，跳转到登录页
      alert('注册成功，请登录');
      router.push({ name: 'login' });
    } else {
      // 注册失败
      error.value = response.data.message || '注册失败，请重试';
    }
  } catch (err) {
    error.value = err.response?.data?.message || '注册失败，请检查网络连接';
    console.error('注册失败:', err);
  } finally {
    loading.value = false;
  }
};

const goToLogin = () => {
  router.push({ name: 'login' });
};
</script>

<template>
  <div class="register-container">
    <div class="register-form">
      <h2>注册系统</h2>
      
      <div class="form-group">
        <label for="username">用户名</label>
        <input
          type="text"
          id="username"
          v-model="username"
          placeholder="请输入用户名"
          @keyup.enter="handleRegister"
        />
      </div>
      
      <div class="form-group">
        <label for="password">密码</label>
        <input
          type="password"
          id="password"
          v-model="password"
          placeholder="请输入密码（不少于6位）"
          @keyup.enter="handleRegister"
        />
      </div>
      
      <div class="form-group">
        <label for="confirmPassword">确认密码</label>
        <input
          type="password"
          id="confirmPassword"
          v-model="confirmPassword"
          placeholder="请再次输入密码"
          @keyup.enter="handleRegister"
        />
      </div>
      
      <div class="form-group">
        <label for="nickname">昵称</label>
        <input
          type="text"
          id="nickname"
          v-model="nickname"
          placeholder="请输入昵称（可选）"
          @keyup.enter="handleRegister"
        />
      </div>
      
      <div class="form-group">
        <label for="email">邮箱</label>
        <input
          type="email"
          id="email"
          v-model="email"
          placeholder="请输入邮箱（可选）"
          @keyup.enter="handleRegister"
        />
      </div>
      
      <div class="form-group">
        <label for="phone">手机号</label>
        <input
          type="tel"
          id="phone"
          v-model="phone"
          placeholder="请输入手机号（可选）"
          @keyup.enter="handleRegister"
        />
      </div>
      
      <div v-if="error" class="error-message">
        {{ error }}
      </div>
      
      <div class="button-group">
        <button
          @click="handleRegister"
          class="register-btn"
          :disabled="loading"
        >
          {{ loading ? '注册中...' : '注册' }}
        </button>
        
        <button
          @click="goToLogin"
          class="login-btn"
        >
          已有账号？去登录
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.register-form {
  background-color: white;
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}

.register-form h2 {
  text-align: center;
  margin-bottom: 30px;
  color: #42b883;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: #555;
}

.form-group input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
  transition: border-color 0.3s;
}

.form-group input:focus {
  outline: none;
  border-color: #42b883;
  box-shadow: 0 0 0 2px rgba(66, 184, 131, 0.2);
}

.error-message {
  color: #f56c6c;
  margin-bottom: 20px;
  text-align: center;
  font-size: 14px;
}

.button-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.register-btn {
  width: 100%;
  padding: 12px;
  background-color: #42b883;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.register-btn:hover:not(:disabled) {
  background-color: #3aa876;
}

.register-btn:disabled {
  background-color: #95d4b4;
  cursor: not-allowed;
}

.login-btn {
  width: 100%;
  padding: 12px;
  background-color: #f0f2f5;
  color: #606266;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s;
}

.login-btn:hover {
  background-color: #e4e7ed;
  color: #409eff;
  border-color: #c6e2ff;
}
</style>