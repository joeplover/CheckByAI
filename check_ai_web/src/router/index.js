import { createRouter, createWebHistory } from 'vue-router';
import Login from '../components/Login.vue';
import Register from '../components/Register.vue';
import MainPage from '../components/MainPage.vue';

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login' // 默认重定向到登录页
    },
    {
      path: '/login',
      name: 'login',
      component: Login,
      meta: {
        title: '登录 - AI Check System'
      }
    },
    {
      path: '/register',
      name: 'register',
      component: Register,
      meta: {
        title: '注册 - AI Check System'
      }
    },
    {
      path: '/main',
      name: 'main',
      component: MainPage,
      meta: {
        title: '首页 - AI Check System',
        requiresAuth: true // 需要登录才能访问
      }
    }
  ]
});

// 路由守卫 - 检查是否需要登录
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title || 'AI Check System';
  
  // 检查路由是否需要登录
  if (to.meta.requiresAuth) {
    // 检查是否有token
    const token = localStorage.getItem('token');
    if (token) {
      // 有token，允许访问
      next();
    } else {
      // 没有token，重定向到登录页
      next({ name: 'login' });
    }
  } else {
    // 不需要登录，直接访问
    next();
  }
});

export default router;