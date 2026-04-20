import { createRouter, createWebHistory } from 'vue-router';
import Login from '../components/Login.vue';
import Register from '../components/Register.vue';
import Layout from '../components/Layout.vue';
import Dashboard from '../components/Dashboard.vue';
import LogisticsManagement from '../components/LogisticsManagement.vue';
import DataMonitor from '../components/DataMonitor.vue';
import FileUpload from '../components/FileUpload.vue';
import AiAssistant from '../components/AiAssistant.vue';
import ReviewWorkbench from '../components/ReviewWorkbench.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/login',
      name: 'login',
      component: Login,
      meta: {
        title: '登录 - 智慧物流系统'
      }
    },
    {
      path: '/register',
      name: 'register',
      component: Register,
      meta: {
        title: '注册 - 智慧物流系统'
      }
    },
    {
      path: '/',
      component: Layout,
      meta: {
        requiresAuth: true
      },
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: Dashboard,
          meta: {
            title: '仪表盘 - 智慧物流系统'
          }
        },
        {
          path: 'logistics',
          name: 'logistics',
          component: LogisticsManagement,
          meta: {
            title: '物流管理 - 智慧物流系统'
          }
        },
        {
          path: 'main',
          name: 'main',
          component: FileUpload,
          meta: {
            title: '文件处理 - 智慧物流系统'
          }
        },
        {
          path: 'reviews',
          name: 'reviews',
          component: ReviewWorkbench,
          meta: {
            title: '复核工作台 - 智慧物流系统'
          }
        },
        {
          path: 'monitor',
          name: 'monitor',
          component: DataMonitor,
          meta: {
            title: '数据监控 - 智慧物流系统'
          }
        },
        {
          path: 'ai-assistant',
          name: 'ai-assistant',
          component: AiAssistant,
          meta: {
            title: 'AI 助手 - 智慧物流系统'
          }
        }
      ]
    }
  ]
});

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || '智慧物流系统';

  if (to.meta.requiresAuth) {
    const token = localStorage.getItem('token');
    if (token) {
      next();
    } else {
      next({ name: 'login' });
    }
  } else {
    next();
  }
});

export default router;
