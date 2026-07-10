<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NCard, NForm, NFormItem, NInput, NSpin, useMessage } from 'naive-ui'
import { errorMessage } from '../services/api'
import { useAuthStore } from '../store/auth'

const auth = useAuthStore()
const message = useMessage()
const username = ref('admin')
const password = ref('admin123')

const submit = async () => {
  if (!username.value.trim() || !password.value) {
    message.warning('请输入用户名和密码')
    return
  }
  try {
    await auth.login(username.value, password.value)
    message.success('登录成功')
  } catch (error) {
    message.error(errorMessage(error))
  }
}
</script>

<template>
  <div v-if="!auth.ready" class="auth-loading"><n-spin size="large" /></div>
  <div v-else-if="!auth.isAuthenticated" class="auth-overlay">
    <n-card class="auth-card" title="内涝应急管理系统" :bordered="false">
      <p>请登录后进入调度工作台</p>
      <n-form @submit.prevent="submit">
        <n-form-item label="用户名">
          <n-input v-model:value="username" autocomplete="username" @keyup.enter="submit" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="password" type="password" show-password-on="click" autocomplete="current-password" @keyup.enter="submit" />
        </n-form-item>
        <n-button type="primary" block :loading="auth.loading" attr-type="submit">登录</n-button>
      </n-form>
      <small>课程演示账号：admin / admin123</small>
    </n-card>
  </div>
</template>

<style scoped>
.auth-loading,
.auth-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: grid;
  place-items: center;
  background: rgba(238, 243, 248, 0.96);
}
.auth-card { width: min(390px, calc(100vw - 32px)); box-shadow: 0 24px 70px rgba(15, 23, 42, 0.18); }
.auth-card p { margin: 0 0 18px; color: #64748b; }
.auth-card small { display: block; margin-top: 14px; color: #94a3b8; }
</style>
