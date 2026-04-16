<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();

// 检查本地存储中是否有token
const checkLoginStatus = () => {
  const token = localStorage.getItem('token');
  if (token) {
    // 有token，跳转到首页
    router.push({ name: 'main' });
  } else {
    // 没有token，跳转到登录页
    router.push({ name: 'login' });
  }
};

onMounted(() => {
  checkLoginStatus();
});
</script>

<template>
  <div id="app">
    <!-- 路由视图，显示当前路由对应的组件 -->
    <router-view />
  </div>
</template>

<style>
#app {
  width: 100vw;
  height: 100vh;
  margin: 0;
  padding: 0;
  overflow: hidden;
}
</style>
