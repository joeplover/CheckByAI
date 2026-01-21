<script setup>
import { ref } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';

const emit = defineEmits(['login']);
const router = useRouter();

const username = ref('');
const password = ref('');
const error = ref('');
const loading = ref(false);

const goToRegister = () => {
  router.push({ name: 'register' });
};

const handleLogin = async () => {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码';
    return;
  }

  loading.value = true;
  error.value = '';

  try {
    // 调用后端的登录API获取JWT token
    console.log('登录请求:', { username: username.value, password: password.value });
    
    const response = await axios.post('http://checkbyai.free.idcfengye.com/auth/login', {
      username: username.value,
      password: password.value
    });
    
    if (response.data.success) {
      // 保存JWT token到本地存储
      localStorage.setItem('token', response.data.token);
      localStorage.setItem('userId', response.data.userId);
      localStorage.setItem('username', response.data.username);
      localStorage.setItem('nickname', response.data.nickname);
      
      // 登录成功，跳转到首页
      router.push({ name: 'main' });
    } else {
      // 登录失败
      error.value = response.data.message || '登录失败，请重试';
    }
  } catch (err) {
    error.value = err.response?.data?.message || '登录失败，请检查网络连接';
    console.error('登录失败:', err);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="login-container">
    <div class="login-form">
      <h2>登录系统</h2>
      <div class="form-group">
        <label for="username">用户名</label>
        <input
          type="text"
          id="username"
          v-model="username"
          placeholder="请输入用户名"
          @keyup.enter="handleLogin"
        />
      </div>
      <div class="form-group">
        <label for="password">密码</label>
        <input
          type="password"
          id="password"
          v-model="password"
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
        />
      </div>
      <div v-if="error" class="error-message">
        {{ error }}
      </div>
      <button
        @click="handleLogin"
        class="login-btn"
        :disabled="loading"
      >
        {{ loading ? '登录中...' : '登录' }}
      </button>
      
      <div class="register-link">
        没有账号？<a href="#" @click.prevent="goToRegister">立即注册</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.login-form {
  background-color: white;
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}

.login-form h2 {
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

.login-btn {
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

.login-btn:hover:not(:disabled) {
  background-color: #3aa876;
}

.login-btn:disabled {
  background-color: #95d4b4;
  cursor: not-allowed;
}

.register-link {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: #606266;
}

.register-link a {
  color: #409eff;
  text-decoration: none;
  transition: color 0.3s;
}

.register-link a:hover {
  color: #66b1ff;
  text-decoration: underline;
}
</style>
