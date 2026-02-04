<script setup>
import { ref, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();
const sidebarCollapsed = ref(false);

const username = computed(() => localStorage.getItem('username') || '用户');

const menuItems = [
  { path: '/dashboard', icon: '📊', label: '仪表盘' },
  { path: '/logistics', icon: '📦', label: '物流管理' },
  { path: '/main', icon: '📁', label: '文件处理' },
  { path: '/monitor', icon: '📈', label: '数据监控' }
];

const isActive = (path) => route.path === path;

const navigateTo = (path) => {
  router.push(path);
};

const handleLogout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('userId');
  localStorage.removeItem('username');
  localStorage.removeItem('nickname');
  router.push({ name: 'login' });
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};
</script>

<template>
  <div class="layout">
    <div :class="['sidebar', { collapsed: sidebarCollapsed }]">
      <div class="sidebar-header">
        <div class="logo">
          <span class="logo-icon">🚚</span>
          <span v-if="!sidebarCollapsed" class="logo-text">智慧物流</span>
        </div>
        <button @click="toggleSidebar" class="toggle-btn">
          {{ sidebarCollapsed ? '→' : '←' }}
        </button>
      </div>
      
      <nav class="sidebar-nav">
        <div
          v-for="item in menuItems"
          :key="item.path"
          :class="['nav-item', { active: isActive(item.path) }]"
          @click="navigateTo(item.path)"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span v-if="!sidebarCollapsed" class="nav-label">{{ item.label }}</span>
        </div>
      </nav>
    </div>

    <div class="main-content">
      <header class="top-bar">
        <div class="top-bar-left">
          <h1 class="page-title">{{ route.meta.title || '智慧物流系统' }}</h1>
        </div>
        <div class="top-bar-right">
          <div class="user-info">
            <span class="user-icon">👤</span>
            <span class="user-name">{{ username }}</span>
          </div>
          <button @click="handleLogout" class="logout-btn">
            退出登录
          </button>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  width: 100%;
  height: 100vh;
  background-color: var(--neutral-50);
  margin: 0;
  padding: 0;
}

.sidebar {
  width: 260px;
  background: linear-gradient(180deg, var(--primary-600) 0%, var(--primary-700) 100%);
  color: white;
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  box-shadow: var(--shadow-md);
  z-index: var(--z-sticky);
}

.sidebar.collapsed {
  width: 80px;
}

.sidebar-header {
  padding: var(--space-5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.logo-icon {
  font-size: 28px;
}

.logo-text {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
}

.toggle-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: background var(--transition-fast);
}

.toggle-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.05);
}

.sidebar-nav {
  flex: 1;
  padding: var(--space-6) 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  cursor: pointer;
  transition: all var(--transition-normal);
  border-radius: var(--radius-xl);
  margin: 0 var(--space-4);
  position: relative;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.12);
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.15);
  box-shadow: var(--shadow-md);
  font-weight: var(--font-semibold);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 60%;
  background: white;
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
}

.nav-icon {
  font-size: 20px;
  min-width: 20px;
}

.nav-label {
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.top-bar {
  background: white;
  padding: var(--space-4) var(--space-6);
  box-shadow: var(--shadow-sm);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.top-bar-left {
  flex: 1;
}

.page-title {
  margin: 0;
  color: var(--neutral-800);
  font-size: var(--text-xl);
  font-weight: var(--font-semibold);
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: var(--neutral-100);
  border-radius: var(--radius-full);
  transition: all var(--transition-fast);
}

.user-info:hover {
  background: var(--neutral-200);
}

.user-icon {
  font-size: 20px;
}

.user-name {
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--neutral-800);
}

.logout-btn {
  padding: var(--space-2) var(--space-4);
  background: var(--danger-500);
  color: white;
  border: none;
  border-radius: var(--radius-lg);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  transition: all var(--transition-fast);
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.logout-btn:hover {
  background: var(--danger-600);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 0;
  background-color: var(--neutral-50);
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .sidebar {
    width: 220px;
  }
  
  .nav-item {
    padding: var(--space-3) var(--space-4);
  }
  
  .nav-label {
    font-size: var(--text-xs);
  }
  
  .content {
    padding: var(--space-5);
  }
}

@media (max-width: 768px) {
  .sidebar.collapsed {
    width: 60px;
  }
  
  .logo-text {
    font-size: var(--text-sm);
  }
  
  .top-bar {
    padding: var(--space-3) var(--space-4);
  }
  
  .page-title {
    font-size: var(--text-lg);
  }
  
  .content {
    padding: var(--space-4);
  }
  
  .user-name {
    display: none;
  }
  
  .logout-btn {
    padding: var(--space-2) var(--space-3);
    font-size: var(--text-xs);
  }
}
</style>
