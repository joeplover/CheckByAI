<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const router = useRouter();
const route = useRoute();
const sidebarCollapsed = ref(false);

const username = computed(() => localStorage.getItem('username') || '用户');

const menuItems = [
  { path: '/dashboard', icon: '📊', label: '仪表盘' },
  { path: '/logistics', icon: '🚚', label: '物流管理' },
  { path: '/main', icon: '📁', label: '文件处理' },
  { path: '/reviews', icon: '🧾', label: '复核工作台' },
  { path: '/monitor', icon: '📈', label: '数据监控' },
  { path: '/ai-assistant', icon: '🤖', label: 'AI 助手' }
];

const currentPageTitle = computed(() => route.meta.title || 'CheckByAI 管理平台');

const isActive = (path) => route.path === path || route.path.startsWith(`${path}/`);

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
  <div class="app-shell">
    <header class="top-nav">
      <div class="top-nav-left">
        <button @click="toggleSidebar" class="menu-toggle">
          {{ sidebarCollapsed ? '☰' : '≡' }}
        </button>
        <div class="brand">
          <span class="brand-dot"></span>
          <span class="brand-text">CheckByAI</span>
        </div>
        <span class="page-title">{{ currentPageTitle }}</span>
      </div>
      <div class="top-nav-right">
        <div class="user-chip">
          <span class="user-avatar">👤</span>
          <span class="user-name">{{ username }}</span>
        </div>
        <button @click="handleLogout" class="logout-btn">
          退出登录
        </button>
      </div>
    </header>

    <div class="shell-body">
      <aside :class="['side-nav', { collapsed: sidebarCollapsed }]">
        <nav class="menu-list">
          <button
            v-for="item in menuItems"
            :key="item.path"
            :class="['menu-item', { active: isActive(item.path) }]"
            @click="navigateTo(item.path)"
          >
            <span class="menu-icon">{{ item.icon }}</span>
            <span v-if="!sidebarCollapsed" class="menu-label">{{ item.label }}</span>
          </button>
        </nav>
      </aside>

      <main class="view-area">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  width: 100%;
  height: 100vh;
  min-width: 1280px;
  display: flex;
  flex-direction: column;
  background: #edf4ff;
}

.top-nav {
  height: 66px;
  background: #ffffff;
  border-bottom: 1px solid #dbe8ff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 2px 10px rgba(63, 117, 214, 0.08);
}

.top-nav-left,
.top-nav-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.menu-toggle {
  width: 34px;
  height: 34px;
  border: 1px solid #d5e5ff;
  border-radius: 8px;
  background: #f7fbff;
  color: #2f5da8;
  padding: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 12px;
  border-right: 1px solid #e5efff;
}

.brand-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5ea6ff, #3f78d7);
}

.brand-text {
  font-size: 18px;
  font-weight: 700;
  color: #2f5da8;
}

.page-title {
  color: #43679f;
  font-size: 15px;
  font-weight: 600;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #f3f8ff;
  color: #2f5da8;
}

.user-avatar {
  font-size: 14px;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.logout-btn {
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid #d6e6ff;
  background: #f7fbff;
  color: #315faa;
  font-size: 13px;
  font-weight: 600;
}

.logout-btn:hover {
  background: #eaf3ff;
}

.shell-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

.side-nav {
  width: 228px;
  background: #f8fbff;
  border-right: 1px solid #dbe8ff;
  padding: 16px 12px;
  transition: width 0.2s ease;
  overflow: hidden;
}

.side-nav.collapsed {
  width: 70px;
}

.menu-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.menu-item {
  width: 100%;
  border: none;
  background: transparent;
  border-radius: 10px;
  height: 40px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #4d6fa4;
  justify-content: flex-start;
}

.menu-item:hover {
  background: #eef5ff;
}

.menu-item.active {
  background: linear-gradient(90deg, #e0efff, #eef6ff);
  color: #2f5da8;
  border: 1px solid #cfe3ff;
}

.menu-icon {
  width: 20px;
  text-align: center;
  font-size: 16px;
}

.menu-label {
  font-size: 14px;
  font-weight: 600;
}

.view-area {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 18px;
  background: #edf4ff;
}
</style>
