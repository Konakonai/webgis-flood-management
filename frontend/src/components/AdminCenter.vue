<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Activity, AlertTriangle, Building2, ChevronLeft, ChevronRight, ClipboardList,
  Database, RefreshCw, Save, Search, ShieldCheck, SlidersHorizontal, Truck, Users
} from 'lucide-vue-next'
import { NButton, NIcon, NInput, NSelect, NSkeleton, NSwitch, NTag, useMessage } from 'naive-ui'
import { apiRequest, errorMessage } from '../services/api'

type TabKey = 'overview' | 'warnings' | 'users' | 'assets' | 'config' | 'logs'
interface PageData<T> { records: T[]; total: number; current: number; size: number; pages: number }
interface User { id: number; username: string; realName?: string; email?: string; phone?: string; enabled: boolean; createdAt?: string }
interface Warning { id: number; title: string; content?: string; warningLevel: number; status: string; createdAt?: string; stationName?: string }
interface Station { id: number; name: string; stationType: string; area?: string; status: string; address?: string }
interface Resource { id: number; name: string; resourceType: string; quantity?: number; unit?: string; status: string; area?: string }
interface LogRecord { id: number; username?: string; action: string; module?: string; description?: string; result?: string; createdAt?: string }

const message = useMessage()
const tabs = [
  { key: 'overview' as const, label: '管理概览', icon: Activity },
  { key: 'warnings' as const, label: '预警处置', icon: AlertTriangle },
  { key: 'users' as const, label: '用户管理', icon: Users },
  { key: 'assets' as const, label: '站点与资源', icon: Database },
  { key: 'config' as const, label: '阈值配置', icon: SlidersHorizontal },
  { key: 'logs' as const, label: '审计日志', icon: ClipboardList }
]
const activeTab = ref<TabKey>('overview')
const loading = ref(false)
const users = ref<PageData<User>>({ records: [], total: 0, current: 1, size: 10, pages: 0 })
const warnings = ref<PageData<Warning>>({ records: [], total: 0, current: 1, size: 10, pages: 0 })
const stations = ref<PageData<Station>>({ records: [], total: 0, current: 1, size: 20, pages: 0 })
const resources = ref<PageData<Resource>>({ records: [], total: 0, current: 1, size: 20, pages: 0 })
const logs = ref<PageData<LogRecord>>({ records: [], total: 0, current: 1, size: 15, pages: 0 })
const userKeyword = ref('')
const warningStatus = ref<string | null>(null)
const assetView = ref<'stations' | 'resources'>('stations')
const configValues = ref<Record<string, string>>({})
const configKeys = [
  { key: 'water_level_warning', label: '水位预警阈值', unit: 'm' },
  { key: 'water_level_danger', label: '水位危险阈值', unit: 'm' },
  { key: 'rain_warning', label: '降雨预警阈值', unit: 'mm/h' },
  { key: 'rain_danger', label: '降雨危险阈值', unit: 'mm/h' }
]
const configLabels = computed(() => configKeys.map((item) => ({ ...item, value: configValues.value[item.key] || '' })))

const query = (values: Record<string, string | number | null | undefined>) => {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') params.set(key, String(value))
  })
  return params.toString()
}
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
const statusLabel = (status: string) => ({
  PENDING: '待处理', CONFIRMED: '已确认', REJECTED: '已驳回', PUBLISHED: '已发布', REVOKED: '已撤销',
  ONLINE: '在线', OFFLINE: '离线', MAINTENANCE: '维护中', AVAILABLE: '可用', DISPATCHED: '已派发', DEPLETED: '已耗尽'
}[status] || status)
const tagType = (status: string): 'success' | 'warning' | 'error' | 'info' | 'default' => {
  if (['ONLINE', 'AVAILABLE', 'PUBLISHED', 'CONFIRMED'].includes(status)) return 'success'
  if (['PENDING', 'DISPATCHED', 'MAINTENANCE'].includes(status)) return 'warning'
  if (['REJECTED', 'OFFLINE', 'DEPLETED'].includes(status)) return 'error'
  return 'default'
}

const loadAll = async () => {
  loading.value = true
  try {
    const [u, w, s, r, l] = await Promise.all([
      apiRequest<PageData<User>>('/api/users?page=1&size=10'),
      apiRequest<PageData<Warning>>('/api/warnings?page=1&size=10'),
      apiRequest<PageData<Station>>('/api/stations?page=1&size=20'),
      apiRequest<PageData<Resource>>('/api/resources?page=1&size=20'),
      apiRequest<PageData<LogRecord>>('/api/logs?page=1&size=15')
    ])
    users.value = u; warnings.value = w; stations.value = s; resources.value = r; logs.value = l
    await loadConfigs()
  } catch (error) { message.error(errorMessage(error)) } finally { loading.value = false }
}
const loadUsers = async (page = 1) => { users.value = await apiRequest(`/api/users?${query({ page, size: 10, keyword: userKeyword.value.trim() })}`) }
const loadWarnings = async (page = 1) => { warnings.value = await apiRequest(`/api/warnings?${query({ page, size: 10, status: warningStatus.value })}`) }
const loadLogs = async (page = 1) => { logs.value = await apiRequest(`/api/logs?${query({ page, size: 15 })}`) }
const loadConfigs = async () => {
  const settled = await Promise.allSettled(configKeys.map((item) => apiRequest<{ key: string; value: string }>(`/api/config/${item.key}`)))
  settled.forEach((result, index) => { if (result.status === 'fulfilled') configValues.value[configKeys[index].key] = result.value.value })
}
const refresh = async () => {
  loading.value = true
  try {
    if (activeTab.value === 'users') await loadUsers(users.value.current)
    else if (activeTab.value === 'warnings') await loadWarnings(warnings.value.current)
    else if (activeTab.value === 'logs') await loadLogs(logs.value.current)
    else await loadAll()
    message.success('数据已刷新')
  } catch (error) { message.error(errorMessage(error)) } finally { loading.value = false }
}
const toggleUser = async (user: User, enabled: boolean) => {
  try {
    await apiRequest(`/api/users/${user.id}/status?enabled=${enabled}`, { method: 'PUT' })
    user.enabled = enabled
    message.success(enabled ? '用户已启用' : '用户已禁用')
  } catch (error) { message.error(errorMessage(error)) }
}
const transitionWarning = async (warning: Warning, action: 'confirm' | 'reject' | 'publish' | 'revoke') => {
  try {
    const body = action === 'publish' ? JSON.stringify({ content: warning.content || warning.title }) : '{}'
    await apiRequest(`/api/warnings/${warning.id}/${action}`, { method: 'PUT', body })
    message.success('预警状态已更新')
    await loadWarnings(warnings.value.current)
  } catch (error) { message.error(errorMessage(error)) }
}
const configNumber = (raw: string) => { try { return JSON.parse(raw).value ?? '' } catch { return '' } }
const setConfigNumber = (key: string, value: string) => {
  let config: Record<string, unknown> = {}
  try { config = JSON.parse(configValues.value[key]) } catch { /* 使用最小有效配置 */ }
  configValues.value[key] = JSON.stringify({ ...config, value: Number(value) })
}
const saveConfig = async (key: string) => {
  try {
    await apiRequest(`/api/config/${key}`, { method: 'PUT', body: JSON.stringify({ value: configValues.value[key] }) })
    message.success('阈值配置已保存')
  } catch (error) { message.error(errorMessage(error)) }
}
const changeTab = (key: TabKey) => { activeTab.value = key }
onMounted(loadAll)
</script>

<template>
  <section class="admin-center">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <span class="brand-mark"><ShieldCheck /></span>
        <div><strong>后台管理中心</strong><small>ADMIN CONSOLE</small></div>
      </div>
      <nav aria-label="后台管理导航">
        <button v-for="tab in tabs" :key="tab.key" :class="{ active: activeTab === tab.key }" @click="changeTab(tab.key)">
          <n-icon :component="tab.icon" /><span>{{ tab.label }}</span>
        </button>
      </nav>
      <div class="sidebar-note"><ShieldCheck /><span>仅管理员可访问<br><small>所有变更均写入审计日志</small></span></div>
    </aside>

    <div class="admin-main">
      <header class="admin-heading">
        <div><p>系统治理与业务监控</p><h2>{{ tabs.find((tab) => tab.key === activeTab)?.label }}</h2></div>
        <n-button secondary :loading="loading" @click="refresh"><template #icon><n-icon :component="RefreshCw" /></template>刷新数据</n-button>
      </header>

      <div class="admin-content">
        <template v-if="loading && !users.total"><n-skeleton text :repeat="8" /></template>
        <template v-else-if="activeTab === 'overview'">
          <div class="metric-grid">
            <article><span class="metric-icon green"><Users /></span><div><small>系统用户</small><strong>{{ users.total }}</strong><p>{{ users.records.filter(u => u.enabled).length }} 名当前页用户已启用</p></div></article>
            <article><span class="metric-icon orange"><AlertTriangle /></span><div><small>预警记录</small><strong>{{ warnings.total }}</strong><p>{{ warnings.records.filter(w => w.status === 'PENDING').length }} 条当前页待处置</p></div></article>
            <article><span class="metric-icon blue"><Building2 /></span><div><small>监测站点</small><strong>{{ stations.total }}</strong><p>覆盖监测与空间数据服务</p></div></article>
            <article><span class="metric-icon purple"><Truck /></span><div><small>应急资源</small><strong>{{ resources.total }}</strong><p>{{ resources.records.filter(r => r.status === 'AVAILABLE').length }} 项当前页可用</p></div></article>
          </div>
          <div class="overview-grid">
            <article class="content-card"><div class="card-title"><div><small>RISK CONTROL</small><h3>待处置预警</h3></div><button @click="changeTab('warnings')">查看全部</button></div>
              <div v-if="!warnings.records.length" class="empty-state">暂无预警记录</div>
              <div v-for="item in warnings.records.slice(0, 5)" :key="item.id" class="summary-row"><span class="warning-level">L{{ item.warningLevel }}</span><div><strong>{{ item.title }}</strong><small>{{ formatTime(item.createdAt) }}</small></div><n-tag size="small" :type="tagType(item.status)">{{ statusLabel(item.status) }}</n-tag></div>
            </article>
            <article class="content-card"><div class="card-title"><div><small>AUDIT TRAIL</small><h3>最近管理操作</h3></div><button @click="changeTab('logs')">审计详情</button></div>
              <div v-if="!logs.records.length" class="empty-state">暂无审计记录</div>
              <div v-for="item in logs.records.slice(0, 5)" :key="item.id" class="summary-row"><span class="log-dot"></span><div><strong>{{ item.description || `${item.module || 'SYSTEM'} · ${item.action}` }}</strong><small>{{ item.username || '系统任务' }} · {{ formatTime(item.createdAt) }}</small></div></div>
            </article>
          </div>
        </template>

        <article v-else-if="activeTab === 'warnings'" class="content-card data-card">
          <div class="toolbar"><n-select v-model:value="warningStatus" clearable placeholder="全部状态" :options="[{label:'待处理',value:'PENDING'},{label:'已确认',value:'CONFIRMED'},{label:'已发布',value:'PUBLISHED'},{label:'已驳回',value:'REJECTED'},{label:'已撤销',value:'REVOKED'}]" @update:value="loadWarnings(1)" /></div>
          <div class="table-wrap"><table><thead><tr><th>预警内容</th><th>等级</th><th>状态</th><th>产生时间</th><th>处置</th></tr></thead><tbody><tr v-for="item in warnings.records" :key="item.id"><td><strong>{{ item.title }}</strong><small>{{ item.content || '系统阈值触发预警' }}</small></td><td><span class="warning-level">L{{ item.warningLevel }}</span></td><td><n-tag size="small" :type="tagType(item.status)">{{ statusLabel(item.status) }}</n-tag></td><td>{{ formatTime(item.createdAt) }}</td><td class="actions"><n-button v-if="item.status === 'PENDING'" size="small" type="primary" @click="transitionWarning(item,'confirm')">确认</n-button><n-button v-if="item.status === 'PENDING'" size="small" @click="transitionWarning(item,'reject')">驳回</n-button><n-button v-if="item.status === 'CONFIRMED'" size="small" type="primary" @click="transitionWarning(item,'publish')">发布</n-button><n-button v-if="item.status === 'PUBLISHED'" size="small" @click="transitionWarning(item,'revoke')">撤销</n-button><span v-if="['REJECTED','REVOKED'].includes(item.status)">已归档</span></td></tr></tbody></table></div>
          <div class="pagination"><button :disabled="warnings.current <= 1" @click="loadWarnings(warnings.current-1)"><ChevronLeft /></button><span>第 {{ warnings.current }} / {{ Math.max(warnings.pages,1) }} 页</span><button :disabled="warnings.current >= warnings.pages" @click="loadWarnings(warnings.current+1)"><ChevronRight /></button></div>
        </article>

        <article v-else-if="activeTab === 'users'" class="content-card data-card">
          <div class="toolbar"><n-input v-model:value="userKeyword" clearable placeholder="搜索用户名或姓名" @keyup.enter="loadUsers(1)"><template #prefix><n-icon :component="Search" /></template></n-input><n-button type="primary" @click="loadUsers(1)">查询</n-button></div>
          <div class="table-wrap"><table><thead><tr><th>用户</th><th>联系方式</th><th>创建时间</th><th>账户状态</th></tr></thead><tbody><tr v-for="item in users.records" :key="item.id"><td><span class="user-avatar">{{ (item.realName || item.username).slice(0,1) }}</span><strong>{{ item.realName || item.username }}</strong><small>@{{ item.username }}</small></td><td>{{ item.email || item.phone || '未填写' }}</td><td>{{ formatTime(item.createdAt) }}</td><td><n-switch :value="item.enabled" @update:value="toggleUser(item,$event)"><template #checked>启用</template><template #unchecked>禁用</template></n-switch></td></tr></tbody></table></div>
          <div class="pagination"><button :disabled="users.current <= 1" @click="loadUsers(users.current-1)"><ChevronLeft /></button><span>第 {{ users.current }} / {{ Math.max(users.pages,1) }} 页</span><button :disabled="users.current >= users.pages" @click="loadUsers(users.current+1)"><ChevronRight /></button></div>
        </article>

        <article v-else-if="activeTab === 'assets'" class="content-card data-card">
          <div class="toolbar segmented"><button :class="{active:assetView==='stations'}" @click="assetView='stations'"><Building2 />监测站点</button><button :class="{active:assetView==='resources'}" @click="assetView='resources'"><Truck />应急资源</button></div>
          <div class="table-wrap"><table v-if="assetView==='stations'"><thead><tr><th>站点名称</th><th>类型</th><th>区域</th><th>地址</th><th>状态</th></tr></thead><tbody><tr v-for="item in stations.records" :key="item.id"><td><strong>{{ item.name }}</strong></td><td>{{ item.stationType }}</td><td>{{ item.area || '—' }}</td><td>{{ item.address || '—' }}</td><td><n-tag size="small" :type="tagType(item.status)">{{ statusLabel(item.status) }}</n-tag></td></tr></tbody></table><table v-else><thead><tr><th>资源名称</th><th>类型</th><th>数量</th><th>区域</th><th>状态</th></tr></thead><tbody><tr v-for="item in resources.records" :key="item.id"><td><strong>{{ item.name }}</strong></td><td>{{ item.resourceType }}</td><td>{{ item.quantity ?? '—' }} {{ item.unit || '' }}</td><td>{{ item.area || '—' }}</td><td><n-tag size="small" :type="tagType(item.status)">{{ statusLabel(item.status) }}</n-tag></td></tr></tbody></table></div>
        </article>

        <div v-else-if="activeTab === 'config'" class="config-grid"><article v-for="item in configLabels" :key="item.key" class="content-card config-card"><span class="metric-icon green"><SlidersHorizontal /></span><div><small>{{ item.key }}</small><h3>{{ item.label }}</h3><p>监测数据达到该数值后触发对应级别预警。</p><div class="config-input"><n-input :value="String(configNumber(item.value))" :input-props="{ inputmode: 'decimal' }" @update:value="setConfigNumber(item.key,$event)"><template #suffix>{{ item.unit }}</template></n-input><n-button type="primary" @click="saveConfig(item.key)"><template #icon><n-icon :component="Save" /></template>保存</n-button></div></div></article></div>

        <article v-else class="content-card data-card"><div class="table-wrap"><table><thead><tr><th>操作说明</th><th>操作者</th><th>模块</th><th>动作</th><th>时间</th></tr></thead><tbody><tr v-for="item in logs.records" :key="item.id"><td><strong>{{ item.description || '系统操作' }}</strong><small>{{ item.result || '' }}</small></td><td>{{ item.username || '系统任务' }}</td><td>{{ item.module || 'SYSTEM' }}</td><td><n-tag size="small">{{ item.action }}</n-tag></td><td>{{ formatTime(item.createdAt) }}</td></tr></tbody></table></div><div class="pagination"><button :disabled="logs.current <= 1" @click="loadLogs(logs.current-1)"><ChevronLeft /></button><span>第 {{ logs.current }} / {{ Math.max(logs.pages,1) }} 页</span><button :disabled="logs.current >= logs.pages" @click="loadLogs(logs.current+1)"><ChevronRight /></button></div></article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.admin-center{display:grid;grid-template-columns:230px minmax(0,1fr);height:100%;background:var(--bg-color);color:var(--text-primary);overflow:hidden}.admin-sidebar{display:flex;flex-direction:column;padding:22px 14px;border-right:1px solid var(--border-color);background:var(--bg-panel)}.admin-brand{display:flex;align-items:center;gap:11px;padding:0 8px 24px}.brand-mark{display:grid;place-items:center;width:38px;height:38px;border-radius:10px;color:#fff;background:linear-gradient(145deg,#0f766e,#059669);box-shadow:0 6px 16px rgba(5,150,105,.24)}.brand-mark svg{width:21px}.admin-brand div{display:flex;flex-direction:column}.admin-brand strong{font-size:15px}.admin-brand small,.card-title small,.admin-heading p,.config-card small{color:var(--text-secondary);font-size:9px;letter-spacing:1.2px}.admin-sidebar nav{display:grid;gap:5px}.admin-sidebar nav button{display:flex;align-items:center;gap:11px;width:100%;height:42px;padding:0 13px;border:0;border-radius:7px;color:var(--text-secondary);background:transparent;font:inherit;font-size:13px;cursor:pointer;text-align:left}.admin-sidebar nav button:hover,.admin-sidebar nav button.active{color:var(--primary-color);background:rgba(18,144,100,.1)}.admin-sidebar nav .n-icon{font-size:18px}.sidebar-note{display:flex;gap:9px;margin-top:auto;padding:12px;border:1px solid var(--border-color);border-radius:8px;color:var(--text-secondary);font-size:12px;background:var(--bg-muted)}.sidebar-note svg{flex:0 0 17px;width:17px;color:var(--primary-color)}.sidebar-note small{font-size:10px}.admin-main{min-width:0;overflow:auto}.admin-heading{position:sticky;top:0;z-index:5;display:flex;align-items:center;justify-content:space-between;min-height:82px;padding:0 30px;border-bottom:1px solid var(--border-color);background:var(--bg-panel)}.admin-heading p{margin:0 0 4px}.admin-heading h2{margin:0;font-size:21px}.admin-content{padding:24px 30px 36px}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin-bottom:20px}.metric-grid article{display:flex;align-items:center;gap:14px;min-height:104px;padding:18px;box-sizing:border-box;border:1px solid var(--border-color);border-radius:9px;background:var(--bg-panel);box-shadow:var(--shadow)}.metric-icon{display:grid;place-items:center;flex:0 0 42px;width:42px;height:42px;border-radius:10px}.metric-icon svg{width:21px}.metric-icon.green{color:#059669;background:rgba(5,150,105,.12)}.metric-icon.orange{color:#d97706;background:rgba(217,119,6,.12)}.metric-icon.blue{color:#1677ff;background:rgba(22,119,255,.12)}.metric-icon.purple{color:#8b5cf6;background:rgba(139,92,246,.12)}.metric-grid small{display:block;color:var(--text-secondary);font-size:12px}.metric-grid strong{font-size:26px;line-height:1.25}.metric-grid p{margin:2px 0 0;color:var(--text-secondary);font-size:10px}.overview-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}.content-card{border:1px solid var(--border-color);border-radius:9px;background:var(--bg-panel);box-shadow:var(--shadow);overflow:hidden}.card-title{display:flex;align-items:center;justify-content:space-between;padding:18px 20px;border-bottom:1px solid var(--border-color)}.card-title h3,.config-card h3{margin:3px 0 0;font-size:15px}.card-title button{border:0;color:var(--primary-color);background:transparent;cursor:pointer}.summary-row{display:flex;align-items:center;gap:12px;min-height:60px;padding:0 20px;border-bottom:1px solid var(--border-color)}.summary-row:last-child{border-bottom:0}.summary-row>div{display:flex;min-width:0;flex:1;flex-direction:column}.summary-row strong{overflow:hidden;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.summary-row small,td small{display:block;margin-top:3px;color:var(--text-secondary);font-size:10px}.warning-level{display:inline-grid;place-items:center;min-width:30px;height:25px;border-radius:6px;color:#d97706;background:rgba(217,119,6,.12);font-size:11px;font-weight:700}.log-dot{width:8px;height:8px;border-radius:50%;background:var(--primary-color);box-shadow:0 0 0 4px rgba(18,144,100,.12)}.empty-state{padding:50px;text-align:center;color:var(--text-secondary)}.data-card{min-height:300px}.toolbar{display:flex;gap:10px;align-items:center;padding:16px 18px;border-bottom:1px solid var(--border-color)}.toolbar .n-input{max-width:310px}.toolbar .n-select{width:180px}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;font-size:12px}th{padding:12px 16px;color:var(--text-secondary);background:var(--bg-muted);font-size:10px;font-weight:600;text-align:left;white-space:nowrap}td{padding:13px 16px;border-top:1px solid var(--border-color);vertical-align:middle}td strong{font-size:12px}.actions{display:flex;gap:6px;align-items:center;white-space:nowrap}.user-avatar{display:inline-grid;place-items:center;width:30px;height:30px;margin-right:9px;border-radius:50%;color:#fff;background:linear-gradient(145deg,#0f766e,#059669);font-weight:700}.pagination{display:flex;align-items:center;justify-content:flex-end;gap:12px;padding:14px 18px;border-top:1px solid var(--border-color);color:var(--text-secondary);font-size:11px}.pagination button{display:grid;place-items:center;width:28px;height:28px;border:1px solid var(--border-color);border-radius:5px;color:var(--text-primary);background:var(--bg-panel);cursor:pointer}.pagination button:disabled{opacity:.35;cursor:not-allowed}.pagination svg{width:14px}.segmented button{display:flex;align-items:center;gap:6px;padding:8px 13px;border:1px solid var(--border-color);color:var(--text-secondary);background:var(--bg-panel);cursor:pointer}.segmented button:first-child{border-radius:6px 0 0 6px}.segmented button:last-child{margin-left:-11px;border-radius:0 6px 6px 0}.segmented button.active{z-index:1;color:var(--primary-color);border-color:var(--primary-color);background:rgba(18,144,100,.08)}.segmented svg{width:15px}.config-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.config-card{display:flex;gap:16px;padding:22px}.config-card>div{flex:1}.config-card p{color:var(--text-secondary);font-size:12px}.config-input{display:flex;gap:10px;margin-top:18px}.config-input .n-input{max-width:220px}
@media(max-width:1100px){.metric-grid{grid-template-columns:repeat(2,1fr)}.overview-grid{grid-template-columns:1fr}}@media(max-width:760px){.admin-center{grid-template-columns:1fr;grid-template-rows:auto minmax(0,1fr)}.admin-sidebar{padding:10px 12px;border-right:0;border-bottom:1px solid var(--border-color)}.admin-brand,.sidebar-note{display:none}.admin-sidebar nav{display:flex;overflow:auto}.admin-sidebar nav button{flex:0 0 auto;width:auto;height:36px;padding:0 11px}.admin-heading{min-height:68px;padding:0 16px}.admin-content{padding:16px}.metric-grid,.config-grid{grid-template-columns:1fr}.overview-grid{gap:14px}}@media(max-width:480px){.admin-sidebar nav button span{display:none}.admin-sidebar nav button{width:40px;justify-content:center}.metric-grid{grid-template-columns:1fr}.admin-heading h2{font-size:18px}}
</style>
