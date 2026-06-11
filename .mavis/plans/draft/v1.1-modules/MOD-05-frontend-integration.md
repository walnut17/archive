# MOD-05 — 前端集成（Knowledge / Dashboard / 路由 / 配置）

> **接手 agent 只需读本文 + 现有 `frontend/src/` + `MOD-03/04` 响应体 即可开工**
> **本模块纯前端，UI/UX agent**

---

## §0 模块目标

v1.1 前端集成层改造：Knowledge.vue 增强（置信度 + 切换 hint） + Dashboard 双模动画 + application.yml + config.json + router 增量 + 整体前端构建优化。

**本模块相对独立**——不依赖 MOD-02/03/04 的代码实现，只依赖它们**已确认的 API 响应体字段**。

---

## §1 涉及 RI

| RI | 改造 |
|---|---|
| RI-22（置信度 3 级） | Knowledge.vue 显示置信度徽章 |
| RI-23（5 级隐式切换） | Knowledge.vue 显示切换 hint |
| RI-29（主页双模动画） | Dashboard.vue 300ms 过渡 |
| RI-30（LLM 抽字段失败兜底） | ProjectForm.vue 失败 banner + 重试按钮 |
| RI-53（双模动画） | AnimatedModeSwitch.vue 组件 |
| 全部 RI | application.yml + config.json 5 段配置 |

---

## §2 涉及文件（独占清单）

### 2.1 新建（2 个文件）

```
frontend/src/
├── components/
│   └── AnimatedModeSwitch.vue              (新, RI-29/53, 300ms CSS transition)
└── api/
    └── (沿用 archive.ts, 不新建)
```

### 2.2 修改（4 个文件，独占）

```
frontend/src/
├── views/
│   ├── Knowledge.vue                       (改, RI-22/23 加置信度 + hint)
│   ├── Dashboard.vue                       (改, RI-29/53 双模动画)
│   ├── ProjectForm.vue                     (改, RI-30 失败 banner)
│   └── (其他 View 由 MOD-04 独占)
├── store/
│   └── auth.ts                             (不改, MOD-04 notification store 独立)
└── (其他文件由 MOD-04 独占)
```

### 2.3 配置（2 个文件，独占）

```
backend/src/main/resources/application.yml (改, 5 段新增)
config/config.example.json                  (改, 5 字段扩)
docs/GLM-KEY-SETUP.md                       (改, 增 network-dict 段)
```

**总计**：2 新 + 4 改 + 3 配置 = 9 个文件

---

## §3 设计要点

### 3.1 Knowledge.vue 增强（RI-22/23）

**前提**：MOD-03 已保证 `/api/qa/ask` 响应体含 `projectSwitchHint` / `confidenceBadge` 可空字段。

```vue
<!-- views/Knowledge.vue -->
<template>
  <div class="knowledge-page">
    <!-- 顶部项目锁定 pill + 切换 hint -->
    <div v-if="response.projectSwitchHint" class="switch-hint-bar">
      <el-alert
        :title="switchHintText(response.projectSwitchHint)"
        :type="hintType(response.projectSwitchHint)"
        :closable="false"
      />
    </div>
    
    <!-- 答案区 -->
    <div v-if="response" class="answer-card">
      <h3>{{ response.answer }}</h3>
      
      <!-- 置信度徽章 (RI-22) -->
      <div v-if="response.confidenceBadge" class="confidence-badge">
        <el-tag :type="confidenceTagType(response.confidenceBadge)">
          {{ confidenceBadgeText(response.confidenceBadge) }}
        </el-tag>
      </div>
      
      <!-- AgentStepsPanel (v1.0 I-12 已有, 增强显示 switchHint) -->
      <AgentStepsPanel :steps="response.steps" />
    </div>
    
    <!-- 现有功能不破: 折叠/展开/历史/导出 markdown -->
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { qaApi } from '@/api/archive';

const response = ref(null);

const switchHintText = (hint) => {
  return {
    'SAME_PROBABLY': '当前问题可能仍属于当前锁定项目, 请确认',
    'DIFFERENT_PROBABLY': '检测到不同项目, 是否切换?',
    'UNCLEAR': '项目上下文不清晰, 请明确说明',
  }[hint] || hint;
};

const hintType = (hint) => {
  if (hint === 'DIFFERENT_PROBABLY') return 'warning';
  if (hint === 'UNCLEAR') return 'info';
  return 'success';
};

const confidenceTagType = (badge) => {
  return {
    'CONFIRMED': 'success',
    'AI_INFERRED': 'warning',
    'PENDING_REVIEW': 'info',
  }[badge] || '';
};

const confidenceBadgeText = (badge) => {
  return {
    'CONFIRMED': '高置信',
    'AI_INFERRED': 'AI 推测',
    'PENDING_REVIEW': '待人工确认',
  }[badge] || '';
};
</script>
```

**改动行数**：< 50 行（保留现有 Knowledge.vue 全部功能）

### 3.2 Dashboard 双模动画（RI-29/53）

```vue
<!-- components/AnimatedModeSwitch.vue -->
<template>
  <transition name="mode-switch" mode="out-in">
    <div :key="mode" class="mode-content">
      <slot :mode="mode" />
    </div>
  </transition>
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps<{
  mode: 'todo-empty' | 'todo-has'
}>();

const mode = ref(props.mode);
watch(() => props.mode, (newMode) => {
  mode.value = newMode;
});
</script>

<style scoped>
.mode-switch-enter-active,
.mode-switch-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.mode-switch-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}
.mode-switch-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
```

```vue
<!-- views/Dashboard.vue (改) -->
<template>
  <div class="dashboard">
    <!-- 待办数 0 → 1 / N → 0 触发 300ms 过渡 -->
    <AnimatedModeSwitch :mode="todoCount > 0 ? 'todo-has' : 'todo-empty'">
      <template #default="{ mode }">
        <TodoWidget v-if="mode === 'todo-has'" :todos="todos" />
        <EmptyTodoWidget v-else />
      </template>
    </AnimatedModeSwitch>
    
    <!-- 顶部"问点什么"按钮常驻 -->
    <el-button type="primary" @click="$router.push('/knowledge')" class="ask-button">
      问点什么
    </el-button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import AnimatedModeSwitch from '@/components/AnimatedModeSwitch.vue';
import { todoApi } from '@/api/archive';

const todos = ref([]);
const todoCount = ref(0);

onMounted(async () => {
  const res = await todoApi.list();
  todos.value = res.data;
  todoCount.value = todos.value.length;
});
</script>
```

### 3.3 ProjectForm.vue 失败 banner（RI-30）

**前提**：MOD-03 已保证 `/api/projects` POST 失败响应体含 `failureType` 可空字段。

```vue
<!-- views/ProjectForm.vue (改) -->
<template>
  <div class="project-form">
    <!-- 失败 banner -->
    <el-alert
      v-if="failureType"
      :title="failureTitle"
      :type="failureType === 'VALUE_INVALID' ? 'error' : 'warning'"
      :closable="false"
      show-icon
    >
      <template #default>
        <p>{{ failureMessage }}</p>
        <el-button v-if="retryable" type="primary" @click="retry" size="small">
          重试
        </el-button>
      </template>
    </el-alert>
    
    <!-- 现有表单 -->
    <el-form :model="form" @submit.prevent="submit">
      <!-- ... -->
    </el-form>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { projectApi } from '@/api/archive';

const failureType = ref(null);
const failureMessage = ref('');
const retryable = ref(false);

const failureTitle = computed(() => {
  return {
    'API_ERROR': 'LLM 服务异常',
    'PARSE_ERROR': 'LLM 返回解析失败',
    'FIELD_MISSING': '必填字段缺失',
    'VALUE_INVALID': '字段值异常',
    'TIMEOUT': '调用超时',
  }[failureType.value] || '提交失败';
});

const submit = async () => {
  try {
    const res = await projectApi.create(form);
    // 成功
  } catch (e) {
    failureType.value = e.response?.data?.failureType;
    failureMessage.value = e.response?.data?.message;
    retryable.value = e.response?.data?.retryable ?? false;
  }
};

const retry = () => {
  failureType.value = null;
  submit();
};
</script>
```

### 3.4 application.yml 配置

```yaml
# === v1.1 新增（零回归: 全部新 key, 沿用默认值）===

# RI-26 网络查字典
archive:
  network-dict:
    enabled-sources: baidu_baike,wiki
    timeout: 5000
    cache-ttl: 3600

# RI-27 QueryMysql 白名单 + 行数截断 + 数值上限
archive:
  query-mysql:
    max-result-rows: 1000
    max-amount: 1.0E8
    allowed-filter-keys: region,industry,stage,fact_type,time_bucket

# RI-33 乐观锁 v1.1 灰度 (D-3)
archive:
  optimistic-lock:
    strict: false  # v1.1 灰度, v2 多用户切 true

# RI-36/60 数据生命周期
archive:
  retention:
    recycle-days: 30
    archive-years: 1
    long-archive-years: 5
    scan-cron: "0 2 * * *"

# RI-35/59 审计加强
archive:
  audit:
    enabled-types: WRITE,LOGIN,SENSITIVE_VIEW,EXPORT,LLM
    log-stack-trace: false  # 默认 false, debug 时切 true

# RI-39 通知中心
archive:
  notification:
    polling-interval: 30s
    page-size: 20
```

### 3.5 config.example.json 配置

```json
{
  // ... 现有配置
  "archive": {
    "networkDict": {
      "enabledSources": ["baidu_baike", "wiki"],
      "timeout": 5000,
      "cacheTtl": 3600
    },
    "queryMysql": {
      "maxResultRows": 1000,
      "maxAmount": 1.0e8,
      "allowedFilterKeys": ["region", "industry", "stage", "factType", "timeBucket"]
    },
    "optimisticLock": {
      "strict": false
    },
    "retention": {
      "recycleDays": 30,
      "archiveYears": 1,
      "longArchiveYears": 5
    },
    "audit": {
      "enabledTypes": ["WRITE", "LOGIN", "SENSITIVE_VIEW", "EXPORT", "LLM"]
    },
    "notification": {
      "pollingInterval": "30s",
      "pageSize": 20
    }
  }
}
```

### 3.6 GLM-KEY-SETUP.md 增段

```markdown
## v1.1 网络查字典 API Key 配置（D-2）

D-2 拍板：v1.1 实施只配 2 候选（百度百科 + 维基百科），金融百科/互动百科留占位。

### 百度百科 API（v1.1 启用）

1. 申请：https://baike.baidu.com/api
2. 配置：`config.json` 的 `archive.networkDict.cacheTtl` 设值
3. 启用：application.yml `archive.network-dict.enabled-sources` 加 `baidu_baike`

### 维基百科 API（v1.1 启用）

1. 申请：https://www.mediawiki.org/wiki/API:Main_page
2. 配置：同上

### 金融百科 / 互动百科（v1.1 停用占位）

D-2 决策：金融百科 / 互动百科留 "已停用" 占位 entry，业务方后续确认出网策略再启用。
```

---

## §4 验收

### 4.1 前端构建

```bash
cd /workspace/projects-online/frontend
npm run build
# 期望：0 错
```

### 4.2 前端单元测试（如有）

```bash
npm run test:unit
# 期望：现有测例全过 + 新增 AnimatedModeSwitch 测试（如有）
```

### 4.3 关键场景验证（手动 + Cypress/Playwright）

| RI | 场景 | 期望 |
|---|---|---|
| RI-22 | 调 `/api/qa/ask` 答案带 confidence=0.70 | 显示"AI 推测"徽章（warning 类型） |
| RI-22 | 调 `/api/qa/ask` 答案带 confidence=0.50 | 显示"待人工确认"徽章（info 类型） |
| RI-23 | 锁定 PRJ-001，问"江苏那个"（不同项目） | 顶部 hint bar "检测到不同项目, 是否切换?" |
| RI-23 | 锁定 PRJ-001，问"新能源项目"（同项目 conf=0.92） | hint bar "当前问题可能仍属于当前锁定项目, 请确认" |
| RI-29/53 | 待办 0 → 1 | 300ms 过渡动画 |
| RI-29/53 | 顶部"问点什么"按钮 | 常驻 |
| RI-30 | LLM API 4xx | ProjectForm 顶部红 banner + 重试按钮 |
| RI-30 | LLM 字段缺失 | 黄 banner + 重试 |

### 4.4 后端 application.yml 加载验证

```bash
cd /workspace/projects-online
mvn spring-boot:run &
sleep 30
curl http://localhost:8080/actuator/configprops | jq '.contexts[].beans[] | select(.prefix=="archive")'
# 期望：5 段配置全加载
```

### 4.5 完工 checklist

- [ ] 2 新 + 4 改前端全部 commit
- [ ] application.yml + config.example.json + GLM-KEY-SETUP.md 全部 commit
- [ ] `npm run build` 0 错
- [ ] §4.3 关键场景全过
- [ ] 改 `TASKS.md` 状态 → `已完成`

---

## §5 踩坑预警

### 5.1 Knowledge.vue 改造 < 50 行

§13.1.1 / §13.1.2 明确"改造 < 50 行"，**不要**重写 Knowledge.vue。保留 v1.0 I-12 的所有功能（折叠/展开/历史/导出 markdown）。

### 5.2 Dashboard 动画不能引第三方库

300ms CSS transition 用 Element Plus `el-transition` 或 Vue `<transition>`，**不要**引 `anime.js` / `gsap` / `framer-motion`。沿用 v1.0 决策"不引超额设计"。

### 5.3 路由 meta.roles 别忘了

MOD-04 加的 4 个路由（/projects/board /notifications /recycle-bin /admin/import）都要 `meta.roles`，否则普通用户能访问。

### 5.4 application.yml 不能改现有 key

**只新增** `archive.*` 5 段，**不删** / **不改** v1.0 任何 key。零回归。

### 5.5 config.example.json 不能改现有字段

同 5.4，**只新增** `archive.*` 5 字段。

### 5.6 ProjectForm.vue 失败 banner 不要遮住表单

`<el-alert>` 用 `position: sticky; top: 0` 或放在表单上方，但**不要**用 modal/dialog 阻塞表单输入。

### 5.7 切换 hint 文案要本地化

参考 T1 §6.2.1 的 prompt 说明，hint 文案应跟 prompt 一致：
- `SAME_CONFIRMED` → 不显示（自动锁定无感）
- `SAME_PROBABLY` → "当前问题可能仍属于当前锁定项目, 请确认"
- `DIFFERENT_PROBABLY` → "检测到不同项目, 是否切换?"
- `UNCLEAR` → "项目上下文不清晰, 请明确说明"

### 5.8 徽章颜色跟 Element Plus 类型映射

`el-tag` 类型：`success`（绿）/ `warning`（黄）/ `info`（灰）/ `danger`（红）
- `CONFIRMED` → success（绿）
- `AI_INFERRED` → warning（黄）
- `PENDING_REVIEW` → info（灰）

---

## §6 接口契约

### 6.1 给 MOD-04（业务功能）

- `/api/qa/ask` 响应体必须含 `projectSwitchHint` + `confidenceBadge`（MOD-03 已提供，本模块消费）
- `/api/projects` POST 失败响应体必须含 `failureType` + `retryable`（MOD-03 已提供）

### 6.2 给 MOD-06（文档/测试）

- 7 个关键场景（§4.3）+ Cypress/Playwright 集成测试
- application.yml 5 段配置文档同步到 `docs/ENVIRONMENT-DEPENDENCIES.md`

---

*本模块由前端 agent 接手。MOD-01 + MOD-02 + MOD-03 完工后开工（API 响应体已确定）。*