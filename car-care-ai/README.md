# car-care-ai · 车管家 AI 养车顾问服务

基于 **FastAPI + LangChain 1.x** 的 AI 服务，为车管家平台提供养车顾问对话能力。
它不直接对外提供服务——唯一的调用方是 `car-care-server`，前端永远拿不到它的地址。

- 代码：24 个 Python 文件 / 1817 行 ｜ 测试：588 行 / 33 个用例
- 语料：5 篇养车知识 markdown / 276 行 → 切分 30 个向量分片
- 模型：对话 DeepSeek，向量 bge-m3（SiliconFlow），向量库 Chroma 本地持久化

---

## 一、它解决什么问题

车主在平台上的典型困惑是「我该做什么保养、多少钱、什么时候去、我上次那个单子修到哪了」。
这些问题散落在服务项目表、订单表、工单表、知识文档里，本项目的目标是把它们收敛成一个对话入口。

拆成三类能力，对应三种数据来源：

| 能力 | 数据来源 | 为什么这么选 |
|---|---|---|
| 门店 / 项目价格 / 套餐 / 优惠券活动 | car-care-server 内部只读接口 | 复用 Java 的「营业中/在售」过滤口径与 Redis 缓存，口径只有一份 |
| 我的车辆 / 订单 / 工单进度 / 已领券 | Java 随请求下发的 userContext | AI 服务不持有用户态，结构上无法越权读他人数据 |
| 保养周期 / 判废标准 / 故障灯含义 / 平台规则 | 本地 Chroma 向量库（RAG） | 这类知识不在业务库里，且模型容易凭记忆编错周期数字 |

最后一类是「预约草稿」：AI 只把门店、项目、金额、时间整理成结构化草稿推给前端，
用户点确认后由前端调**既有的** `POST /api/orders` 下单。AI 不写库、不碰订单状态机。

---

## 二、启动

### 1. 准备环境

需要 **Python 3.13**（实测 3.13.0 与 3.13.12 均可）。

```bash
cd car-care-ai
python -m venv .venv                     # 建议用独立 venv，别污染 base 环境
.venv/Scripts/python.exe -m pip install -r requirements.txt   # Windows
# source .venv/bin/activate && pip install -r requirements.txt  # macOS / Linux
cp .env.example .env      # 然后填密钥
```

`.env` 需要填的四个值：

| 变量 | 说明 |
|---|---|
| `LLM_API_KEY` | DeepSeek 密钥 |
| `EMBEDDING_API_KEY` | SiliconFlow 密钥（RAG 用 bge-m3） |
| `CARCARE_INTERNAL_TOKEN` | 内部调用令牌，**必须与 car-care-server 的 `carcare.ai.internal-token` 完全一致** |
| `LLM_MODEL` | `provider:model` 格式，默认 `deepseek:deepseek-chat` |

`.env` 已在 `.gitignore` 里，别把它提交上去。

练习项目 `langchain1.2` 和 `lc-course` 的 `.env` 里明文存着真实 key。**那两个目录不在任何 git 仓库里，所以并没有真的泄露过**（查过全盘 9 个仓库的历史，这些 key 一次都没出现过），但它们**都没有 `.gitignore`**——哪天在那边 `git init && git add .`，key 就会跟着进去。这里不重复那个坑。

### 2. 建向量库（可选）

服务启动时若发现向量库为空会自动灌一次语料，所以这步可以跳过。
改了 `app/knowledge/*.md` 之后需要手动重建：

```bash
python scripts/ingest_knowledge.py
```

### 3. 起服务

三种等价写法，挑一个：

```bash
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000   # 标准写法，可加 --reload
python -m app.main                                            # 主机/端口取 .env 里的 HOST / PORT
python app/main.py                                            # 同上，IDE 绿色按钮走的就是这条
```

**在 PyCharm 里用绿色按钮启动**：打开 `app/main.py`，点行号左侧的 ▶。解释器要选到
`car-care-ai/.venv`（Settings → Project → Python Interpreter），工作目录用默认的 `app/` 也能跑。
但**调试器之外的启动方式都是单进程**——要热重载（改代码自动重启）只能用第一条命令加 `--reload`。

这里有两个坑，都踩过：

- `main.py` 原本没有 `if __name__ == "__main__":` 入口。点绿色按钮只是把模块 import 一遍然后**立刻退出**：
  控制台既没有报错也没有日志，看着就像"启动不了"，其实进程正常结束了。现在补上了这个入口。
- `python app/main.py` 这种跑法下 `sys.path[0]` 是 `app/` 而不是项目根，顶部的 `from app import ...`
  会 `ModuleNotFoundError`，所以 `main.py` 开头有一行 sys.path 兜底；用 `-m` 或 uvicorn 启动时不会走到那段。

只绑 `127.0.0.1` 是刻意的：AI 服务不对外暴露，所有流量必须经 car-care-server 网关。
另外 8000 端口同一时刻只能有一个实例，起之前先确认没有残留进程（`netstat -ano | findstr :8000`），
否则报的是 `error while attempting to bind on address ... 10048`。

### 4. 验证

```bash
curl http://127.0.0.1:8000/healthz
# {"status":"ok","model":"deepseek:deepseek-chat","ragReady":true,"ragChunks":30,"detail":null}
```

---

## 三、接口

除 `/healthz` 外都需要请求头 `X-Internal-Token`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/healthz` | 健康检查：模型名、RAG 是否就绪、分片数 |
| POST | `/v1/chat` | 流式对话（SSE），Java 逐帧透传给前端 |
| POST | `/v1/chat/sync` | 非流式对话，调试与单测用 |
| POST | `/v1/rag/reload` | 重建知识库（会消耗 embedding 额度，别在压测循环里调） |
| GET | `/v1/rag/search?q=&k=` | 只检索不生成。**排障第一步**：先确认检索对不对，再怀疑提示词 |

交互式文档在 `/docs`。

### SSE 事件契约

`/v1/chat` 每帧是一个 JSON，前端按 `type` 分支渲染：

| type | 含义 | 前端该做什么 |
|---|---|---|
| `start` | 会话开始 | 记录 `sessionId` |
| `delta` | 正文增量 | 追加到当前气泡 |
| `reset` | **丢弃当前气泡已渲染的正文** | 清空当前气泡 |
| `tool_start` | 开始调用工具（带 `toolLabel`） | 显示「正在查询门店…」 |
| `tool_end` | 工具返回（`toolOk` / `toolSummary`） | 标记成功/失败 |
| `draft` | 预约草稿 | 渲染确认卡片 |
| `done` | 回答结束（完整正文 + token/耗时） | 结束打字机，显示耗时 |
| `error` | 出错（`message` 可直接展示） | 提示用户 |

`reset` 是这里唯一需要解释的事件。模型在调工具前常先说一句「让我查一下门店」，
这句话不该留在最终回答里。但为了保住真流式（不用等生成完才吐字），
做法是先把 `delta` 正常推给前端，一旦发现这一轮出现了工具调用，立刻发 `reset` 让前端清掉。
详见 `document/AI-DESIGN.md` 的「过渡语处理」。

---

## 四、代码结构

```
app/
├── main.py            FastAPI 入口、lifespan、RAG 排障接口、校验失败日志
├── config.py          pydantic-settings 集中配置（替代练习里散落的 os.getenv）
├── schemas.py         Java ↔ Python 的数据契约，与 Java 的 vo/AiUserContextVO 一一对应
├── security.py        内部令牌校验（定长比较 + 未配置即拒绝）
├── agent/
│   ├── builder.py     create_agent 组装：模型 / 工具 / 提示词 / 中间件 / context_schema
│   ├── prompts.py     系统提示词 + 工具展示文案（改工具要同步改这里）
│   ├── middleware.py  日期注入 + 耗时/token 统计
│   ├── context.py     请求级上下文（Agent 无状态可并发复用）
│   └── tools/         11 个工具，按数据来源分 4 类
├── rag/
│   ├── ingest.py      语料加载与切分（按 ## 章节切 + 【文档·章节】前缀）
│   └── store.py       Chroma 向量库、检索、重建，全部优雅降级
├── clients/carcare.py 调 Java 内部接口（失败返回 ToolResult 而不是抛异常）
├── services/chat.py   核心：LangGraph 消息流 → SSE 事件的归一化
└── knowledge/         5 篇语料
```

### 11 个工具

| 工具 | 数据来源 | 触发场景 |
|---|---|---|
| `search_stores` | Java 内部接口 | 有哪些门店 / 哪个城市有店 |
| `search_service_items` | Java 内部接口 | 某个项目多少钱 |
| `get_packages` | Java 内部接口 | 有什么套餐 |
| `list_claimable_coupons` | Java 内部接口 | 有什么优惠活动 |
| `get_my_vehicles` | 下发的 userContext | 我的车 |
| `get_my_coupons` | 下发的 userContext | 我有什么券 |
| `get_my_orders` | 下发的 userContext | 我的订单 |
| `get_order_progress` | 下发的 userContext | 修到哪一步了 |
| `suggest_maintenance_plan` | 下发的 userContext | 我的车该保养了吗 |
| `search_maintenance_knowledge` | 本地 Chroma | 保养周期 / 判废标准 / 故障灯 |
| `build_booking_draft` | 组合上面几个 | 帮我约一下 |

---

## 五、测试

```bash
python -m pytest tests -q
# 33 passed
```

不联网（`/v1/rag/search` 那条会在缺密钥时自动跳过），不依赖 Java 服务。

重点覆盖的是最容易出错的部分：

- `test_chat_events.py`：用脚本化的假 Agent 驱动事件归一化。开发中在这里踩过两个真 bug
  （过渡语泄漏、多轮时 `reset` 完全不触发），两条用例都会失败在修复前的实现上，是真回归保护。
- `test_tools.py`：预约草稿的入参校验——项目不属于该门店必须拒绝，
  上游接口挂了必须如实说而不是编数据。
- `test_api.py`：令牌校验，含「未配置令牌时拒绝一切」这条安全默认值。
- `test_rag_ingest.py`：切分与分片前缀（检索召回依赖它）。

---

## 六、已知边界

诚实列出没做的事，避免面试时被问穿：

- **没有做限流和成本核算**。单次对话的 token 数会随 `done` 事件返回并落库，但没有人按用户/按天汇总，
  也没有配额限制。公网部署前必须补。
- **没有多轮工具结果复用**。每轮对话都重新查门店/项目，没有把上一轮的工具结果缓存进会话。
  对话轮次多时 token 会线性上涨。
- **工具轮数用满时靠兜底文案收尾**。`MAX_TOOL_ROUNDS=5` 用尽后中间件直接结束 Agent，
  模型没机会写最终回答，此时按「有没有草稿」给一句兜底话术（见 `_fallback_answer`）。
  更好的做法是给模型一个「必须现在回答」的强制收尾轮。
- **并发已实测，但上限未知**。1/5/20/50/100 并发各压过一轮（`document/jmeter/ai-chat-test.jmx`）：
  100 并发长连接 **0 断流 0 错误**，495 次请求的会话与消息**零丢失**，Python 回调 Java 内部接口
  也没有死锁。瓶颈定位在上游模型本身（模型调用耗时 1.1 s → 2.6 s），不是本服务。
  但 100 并发仍 0 错误就停了，**第一个出错点在哪仍然没测到**；Python 侧也还没有并发上限保护。
- **知识库靠人工维护**。5 篇语料是手写的，没有从工单/评价里自动挖掘 FAQ 的链路。
- **单轮问题长度上限 2000 字**，超长问题会被 Java 侧拦下。

---

## 七、和 langchain1.2 练习的差别

练习项目是「把每个 API 用一遍」，这个服务是「把它们组装成一个能上线的模块」。补上的部分：

| 练习里没有的 | 这里的做法 |
|---|---|
| 配置散落成 36 处 `os.getenv` | `pydantic-settings` 集中配置 + `.env.example` |
| `SmartAssistant` 把 messages 存在实例字段上 | 请求级 `AgentContext` + `context_schema`，Agent 无状态可并发复用 |
| 只有同步 `invoke` | `astream(stream_mode="messages")` + 事件归一化层 |
| 没有错误处理 | 工具失败返回文本而非抛异常；上游挂了降级为空结果；整链路可降级 |
| 没有服务化 | FastAPI + SSE + 生命周期管理 + 令牌鉴权 |
| 没有测试 | 33 个用例，重点钉住踩过的坑 |
