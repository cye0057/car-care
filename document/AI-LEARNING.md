# AI 模块学习文档：从「调通 API」到「说清楚为什么」

> **这份文档和其他文档的区别**
> - `AI-DESIGN.md` —— 讲清楚**做了什么、为什么这么选**（给面试官和自己复盘用）
> - `car-care-ai/README.md` —— 讲清楚**怎么用**（接口、启动、边界）
> - **本文档 —— 讲清楚「为什么这样是对的」，用来让你自己真正懂**
>
> AI 模块和前面那些模块最大的不同：它的核心链路里有一个**概率性的、会撒谎的、行为不可完全预测**的组件（大模型）。
> 所以这里没有「配置对了就一定对」的确定性，只有「用结构把它的不确定性关进笼子」。
> 这份文档就是讲清楚那些笼子是怎么搭的，以及**怎么亲眼看到笼子起作用**。

---

## 一、怎么用这份文档

**不要从头读到尾。** 按这个顺序用：

1. 先看「学习地图」，确认你现在卡在哪一层
2. 某个概念说不清，去「概念速查」查原理
3. 想真正搞懂某个点，去「逐点深潜」——每个都是**原理 → 代码位置（带行号）→ 动手验证 → 常见误解**四段
4. 「动手实验清单」是这份文档最值钱的部分：你有现成能跑的环境，改一个参数看到变化，比读十遍都管用

**核心原则和 `LEARNING.md` 一样：每个概念都要配一个「我能在屏幕上看到它」的动作。**
AI 模块尤其如此——它的很多行为（模型说了什么、检索命中了什么、工具调了几次）**只存在于运行时**，读代码是看不出来的，必须跑起来看日志和帧。

> 一个前提：本模块的很多现象**只能通过日志和 SSE 帧观察**，不能靠读代码推断。
> 所以先把 `document/tools/sse_probe.py` 跑通（见实验 1），它是你后面所有实验的「显微镜」。

---

## 二、学习地图

按依赖关系排序，**前面的学扎实了，后面的才接得住**：

```
第 0 层  前置（不懂这些，后面全是玄学）
  ├─ HTTP 与 SSE 协议（text/event-stream 的帧格式长什么样）
  ├─ JSON 契约与 pydantic 校验（Java ↔ Python 的字段一一对应）
  ├─ async/await 与事件循环（为什么阻塞操作必须丢线程池）
  └─ 向量与余弦相似度（RAG 用到的数学只有这一点）
        │
        ▼
第 1 层  和大模型说话
  ├─ chat 接口的输入输出结构（messages 数组、三种角色）
  ├─ 流式返回：为什么「首字延迟」是核心体验指标
  ├─ token 与成本（输入/输出分开计价，为什么要把上下文裁剪）
  └─ 温度、思考链开关对输出的影响
        │
        ▼
第 2 层  Function Calling ← 整个 Agent 的地基
  ├─ 模型**不执行**函数，它只吐「想调谁、参数是什么」的 JSON
  ├─ 工具 schema 从哪来（@tool + docstring + 类型标注）
  ├─ Agent 循环：模型 → 工具 → 模型 → …什么时候停
  └─ 中间件与预算控制（为什么用框架自带的限流）
        │
        ▼
第 3 层  流式事件归一化 ← 本项目最容易出错的地方（踩过两个真 bug）
  ├─ stream_mode="messages" 吐的 (chunk, metadata) 是什么
  ├─ tool_call_chunks 分片与 **index 只在单轮内唯一**
  ├─ 过渡语问题与 reset 事件
  └─ 事件契约：前端按 type 分支渲染
        │
        ▼
第 4 层  RAG（检索增强生成）
  ├─ 切分：按结构切 vs 按字数切
  ├─ 向量化与相似度阈值（宁可说不知道，也不要塞错资料）
  ├─ 检索结果怎么进 prompt
  └─ 降级：检索挂了不能把对话一起带崩
        │
        ▼
第 5 层  服务化与跨语言
  ├─ FastAPI + lifespan（预热向量库、释放连接）
  ├─ 无状态 Agent + 请求级上下文（可并发复用的前提）
  ├─ SSE 跨语言透传（Python → Java Reactor → 前端）
  └─ 取消传导（前端断连 → 上游停止生成，不烧 token）
        │
        ▼
第 6 层  工程判断（面试最加分的一层）
  ├─ 哪些数据给模型、哪些不给（裁剪与越权）
  ├─ 超时预算分层（谁先认输）
  ├─ 降级与开关（AI 挂了主链路必须无感）
  └─ 怎么证明它是对的（回归测试 + 并发压测）
```

**如果你时间有限**，优先级是：第 2 层 > 第 4 层 > 第 3 层 > 第 5 层 > 第 6 层。
第 0、1 层是前提不能跳；第 3 层虽然最容易出错，但它是第 2 层的直接后果，理解了第 2 层再回来看会很快。

---

## 三、概念速查

### 3.1 大模型不是数据库：为什么必须给它工具

模型只会根据训练语料「续写」，它**不知道你的门店今天营不营业、某个项目多少钱、你的车跑了多少公里**。
你问它「西湖文一店小保养多少钱」，它不会说不知道，它会**编一个很像样的价格**——这是最危险的地方：错得很自然，用户看不出来。

所以本项目划了一条硬线（`app/agent/prompts.py:35-49` 的「几条硬约束」）：

- 价格、库存、营业状态**一律以工具返回为准**，工具没返回就说「我查不到」，禁止推测；
- 涉及具体数字或判定标准的问题（保养周期、刹车片判废厚度、故障灯含义）**必须先检索知识库再答**。

**一句话记法：模型负责「组织语言」，工具负责「提供事实」。**

### 3.2 Function Calling 的本质：模型只吐 JSON，执行在框架

这是最容易误解的一点。**模型自己不会调用任何函数。** 它的能力只是：

> 在输出里生成一段结构化的 JSON，表示「我想调用 `search_stores`，参数是 `{"city":"杭州"}`」。

真正的执行者是 LangGraph 的运行时：它解析这段 JSON → 找到同名工具 → 执行 → 把结果包成 `ToolMessage` 塞回消息列表 → 再问模型一次。

代码位置：`app/agent/tools/catalog.py` 里的 `@tool` 装饰器负责把函数的**类型标注 + docstring** 变成模型能看懂的「工具说明书」（JSON Schema）。
所以 **docstring 不是写给人看的注释，是写给模型看的接口文档**——写得含糊，模型就选错工具或填错参数。

### 3.3 Agent 循环：什么时候停

一次对话的实际执行过程是**多轮循环**，不是一问一答：

```
用户问题 → 模型（可能吐工具调用）
             ├─ 没有工具调用 → 生成正文 → 结束
             └─ 有工具调用 → 执行工具 → 结果塞回 → 再问模型 → 回到上面
```

停止条件有三个（`app/agent/builder.py:48-64`）：

| 停止条件 | 由谁保证 |
|---|---|
| 模型不再要工具，直接给正文 | 模型自己 |
| 模型调用次数用满 | `ModelCallLimitMiddleware(run_limit=max_tool_rounds+1=6)` |
| 工具调用总数用满 | `ToolCallLimitMiddleware(run_limit=10)` |

**为什么是 6 而不是 5**：`max_tool_rounds=5` 是「工具轮数」，每轮工具之后都要再调一次模型来消化结果，最后还要一次机会写最终回答，所以模型调用上限 = 轮数 + 1。

**为什么用框架自带的限流而不是自己数循环**：`exit_behavior="end"` 会让 Agent **带着现有信息优雅收尾**，而不是抛异常。自己写计数器很容易写出「工具都调成功了，但用户收到一个报错」。

### 3.4 流式：为什么首字延迟比总耗时更重要

同样是 5 秒的回答：

- **非流式**：用户盯着转圈 5 秒，然后文字一次性出现 → 体感「卡了 5 秒」
- **流式**：0.8 秒后开始一个字一个字往外冒 → 体感「反应很快」

两者总耗时一样，体验差一个数量级。所以 `/v1/chat` 用 SSE（Server-Sent Events）逐帧推送，Java 侧也是**逐帧透传**（`AiChatService.java:100-117`），不做任何攒批。

SSE 的帧格式极简：每帧 `data: <内容>\n\n`，空行表示一帧结束。

**这里有个本项目的真坑**：Spring MVC 对 `Flux<String>` 会走 `ReactiveTypeHandler`，**自动**给每个元素加 `data:` 前缀并补空行。
最初我们以为它是「原样写出」，自己拼了 `data: {...}\n\n`，结果被二次包装成 `data:data: {...}`。
所以 `AiChatService.frame()`（`AiChatService.java:202-212`）**只返回裸 JSON**，前缀交给 Spring 加。

### 3.5 `stream_mode="messages"` 吐的是什么

```python
async for chunk, metadata in agent.astream(..., stream_mode="messages"):
    node = metadata["langgraph_node"]   # "model" 还是 "tools"
```

每个元素是二元组：

- `chunk`：消息片段。模型在说话时是 `AIMessageChunk`，工具返回结果时是 `ToolMessage`；
- `metadata`：这段来自图的哪个节点。

**必须靠 `isinstance(chunk, AIMessageChunk)` 分支**，因为两类片段混在同一个流里（`app/services/chat.py:99-150`）。

### 3.6 `tool_call_chunks` 与 index 的作用域（本项目踩过的大坑）

模型吐工具调用时，参数是**分片到达**的，而且**只有第一个分片带 `id` 和 `name`**，续接分片只有 `index` + `args`：

```
chunk1: {"index":0, "id":"call_a", "name":"search_stores", "args":"{\"ci"}   ← 有 id、有 name
chunk2: {"index":0, "id":null,  "name":null,           "args":"ty\":\"杭州\"}"} ← 只有 index 和 args
```

所以要按「调用」把分片攒起来。**键用什么？** 这是踩过两次的坑：

- 用 `index` 做键 → 错。**每一轮的 index 都从 0 重新开始**，第二轮的 `index=0` 会撞上第一轮的残留，导致「首次出现」判断永远失败，`reset` 和 `tool_start` 都不再触发（症状：最终回答里堆满各轮的英文自述）。
- 用工具调用 `id` 做键 → 对。id 是全局唯一的（`app/services/chat.py`）。
- 但**不能写成 `key = tc.get("id") or f"idx{index}"`**：续接分片没有 id，会掉进 `or` 的兜底分支另起一个槽位，于是**参数被攒进了另一个槽位**。症状是「`tool_start` 与 `tool_end` 里永远看不到参数」，模型明明传了 `{"store_id":1,"item_ids":[6]}`，前端和日志里却只有 `{}`/`null`。正确做法是维护一张 **本轮 `index` → `key`** 的映射，续接分片靠它找回同一个槽位，并在本轮结束时随分片缓存一起清掉（`app/services/chat.py` 的 `round_keys`）。

**一句话记法：`index` 的作用域是「单轮」，`id` 的作用域是「整次请求」，而续接分片只有前者。**

### 3.7 过渡语与 `reset` 事件

模型在调工具前常先说一句「让我查一下门店」。这句话不该留在最终回答里，但**又不能等生成完再决定要不要**——那就牺牲了真流式。

本项目的解法（`app/services/chat.py:74, 118-122`）：

1. 正文 `delta` **照常实时推给前端**（保住首字延迟）；
2. 一旦发现这一轮出现了工具调用，立刻推一个 `reset` 事件，让前端**清空当前气泡**；
3. 这一轮的正文也同时从本地累积里丢掉。

代价是前端会短暂闪一下过渡语（通常几十毫秒），换来的是「不用等生成完」。这是个**刻意的取舍**，`reset` 也是整个事件契约里唯一需要解释的事件。

### 3.8 RAG 四步：切分 → 向量化 → 检索 → 拼进 prompt

RAG 的数学部分只有「向量 + 余弦相似度」，剩下的全是工程问题：

| 步骤 | 本项目的做法 | 代码位置 |
|---|---|---|
| 切分 | 先按 markdown 的 `## ` 章节切，过长再按中文标点递归切 | `app/rag/ingest.py:29-34, 46-64` |
| 前缀 | 每片前面拼 `【文档名·章节名】`，**这段前缀也参与向量化** | `app/rag/ingest.py:84-87` |
| 向量化 | bge-m3（SiliconFlow，OpenAI 兼容接口） | `app/rag/store.py:30-36` |
| 检索 | 取 top-k=4，再按阈值 0.35 过滤 | `app/rag/store.py:75-89` |
| 拼进 prompt | 工具返回「参考资料」文本，由模型参考作答 | `app/agent/tools/knowledge.py:27-35` |

**为什么按结构切而不是按字数硬切**：保养知识天然按「机油 / 轮胎 / 刹车」分节，按 `## ` 切不会把一条完整规则从中间劈开。字数切分很容易把「低于 3mm 必须更换」和它的前提条件切到两个片里。

**为什么加前缀**：用户问「刹车片多久换」，如果一个分片正文里没出现「刹车片」三个字（只说「厚度低于 3mm」），纯正文向量化就可能检索不到。前缀把主题信息补进向量，实测召回明显提升。

### 3.9 阈值：宁可让模型说「不知道」，也不要塞错资料

`rag_min_score=0.35` **不是拍脑袋定的**，是实测标定的（`app/config.py:39-42` 有完整注释）：

| 样本类型 | 实测得分区间 |
|---|---|
| 与知识库相关的问题 | 0.43 ~ 0.68 |
| 无关问题（闲聊等） | 0.18 ~ 0.25 |

取 0.35 落在两簇之间的空档。标定时还发现一个**反例**：「安心保返修政策」这种平台规则类问题只拿到 **0.428** 分——如果照搬另一个项目用的 0.6 阈值，这条正确答案会被直接滤掉。

**这就是「阈值必须用自己的语料标定」的实证**：换个 embedding 模型或大改语料，必须重新标定。

### 3.10 无状态 Agent + 请求级上下文

`create_agent` 构建出的 Agent **一次构建、所有请求共用**（`app/agent/builder.py:45-66`）。每个请求的差异通过 `context=` 传入：

```python
agent.astream({"messages": ...}, context=context, stream_mode="messages")
```

工具侧用 `runtime: ToolRuntime[AgentContext]` 拿到它（`app/agent/tools/booking.py:90-92`）。

**为什么必须这样**：练习项目 `langchain1.2` 的 `SmartAssistant` 把 `messages` 存在实例字段上——那种写法在并发下会**串会话**（A 用户的问题进了 B 用户的上下文）。改成请求级 `AgentContext` 之后，同一个 Agent 实例可以安全并发复用，这也是它能扛 100 并发的前提之一。

### 3.11 中间件：挂在 LangGraph 节点上的钩子

| 中间件 | 类型 | 干什么 |
|---|---|---|
| `dated_system_prompt` | `@dynamic_prompt` | 给系统提示词注入「今天几号」 |
| `timing_middleware` | `@wrap_model_call` | 统计模型调用次数 / 耗时 / token |
| `ModelCallLimitMiddleware` | 框架自带 | 模型调用次数上限，硬性截断空转 |
| `ToolCallLimitMiddleware` | 框架自带 | 工具调用总数上限 |

**顺序有意义**：`dated_system_prompt` 必须放最前，它负责改写 system message，后面的模型调用都依赖它（`app/agent/builder.py:52-61` 有注释说明）。

**为什么日期要动态注入而不是写死在提示词常量里**：提示词常量在**进程启动时**就固定了，长驻服务第二天就会带着昨天的日期回答。
这个坑是真踩到的：用户说「这周六去」，模型不知道今天几号，编出了 `2026-06-27`（当天其实是 09-24），预约草稿直接是无效时间。

### 3.12 数据双通道：为什么用户私有数据由 Java 下发

| 数据类型 | 通道 | 原因 |
|---|---|---|
| 门店 / 项目价格 / 套餐 / 券池 | Python 调 Java 内部只读接口 | 复用 Java 的「营业中/在售」过滤口径与 Redis 缓存，口径只有一份 |
| 我的车辆 / 订单 / 工单 / 已领券 | Java 随请求下发 `userContext` | AI 服务不持有用户态 → **越权在结构上不成立** |
| 保养周期 / 判废标准 / 故障灯 | 本地 Chroma 向量库 | 这类知识不在业务库里，模型容易凭记忆编错数字 |

**关键点**：`AiContextService.build(userId)` 里的 `userId` 来自**已通过 JWT 校验的** `BaseContext`，不接受任何来自请求体的 userId（`AiContextService.java:37-46`）。
所以车主**无法**通过改请求参数让 AI 读到别人的订单——这不是「我们检查了权限」，而是「**根本没有按 userId 查数据的入口**」。

这是本项目在安全上最值得讲的一个设计：**把越权从「需要检查」变成「无法表达」**。

### 3.13 超时预算分层：谁先认输

```
Java 建连超时 3s  ≪  模型首字等待  ≪  Python 单请求预算 120s  <  Java 空闲读超时 180s
```

两个细节值得注意：

- Java 的 180s 是 **reactor-netty 的空闲超时**（两次网络读之间的最大间隔），**不是总时长上限**。模型思考 60 秒才吐第一个字属于正常，用「总时长」卡会误杀（`AiWebClientConfig.java:31-42`）。
- **总时长上限刻意放在 Python 侧**（`asyncio.timeout(settings.request_timeout)`）：让**上游先认输**并推一个友好的 `error` 事件，比下游先掐断、上游还在继续烧 token 要干净（这是另一个项目踩过的坑）。

### 3.14 降级：AI 挂了，主链路必须无感

三条降级路径，都能实测：

| 场景 | 表现 |
|---|---|
| `carcare.ai.enabled=false` | `/api/ai/chat` 直接返回友好 error 帧 |
| Python 服务没起来 | `/api/ai/health` 返回 `available:false`；`/api/ai/chat` 返回 error 帧 |
| 向量库检索失败 | 返回空列表，对话继续，只是没有 RAG 能力（`app/rag/store.py:82-86`） |
| 组装用户上下文失败 | 按空上下文继续，车主仍能问保养知识（`AiContextService.java:101-103`） |

**设计原则：AI 是增强能力，不是关键路径。** 下单、支付、查订单这些主链路完全不受 AI 可用性影响。

---

## 四、逐点深潜（原理 → 代码位置 → 动手验证 → 常见误解）

### 4.1 过渡语泄漏 + 多轮 reset 失效（两个真 bug）

**原理**
`reset` 的触发条件是「这一轮模型开始调工具，且这一轮已经吐过正文」。这里有两个独立的坑，我们**先后踩了两次**，而且第二次是修第一次时引入的：

- **坑一（布尔标志不复位）**：最初用一个 `has_tool_call` 布尔标志判断「本轮是否已调工具」，置位后不复位 → 第二轮之后的过渡语再也不会被清掉。
- **坑二（index 撞车）**：改成「逐轮判断」后，`pending_calls` 仍然按 `index` 做键。而**每轮的 index 都从 0 重新开始**，第二轮的 `index=0` 撞上第一轮残留的 slot（`slot["name"]` 已有值），于是 `first_time` 永远为假 → **`reset` 和 `tool_start` 全部不再触发**，最终回答里堆满各轮的英文自述。

修法是两处一起改：`round_text` 逐轮累积/清空（`app/services/chat.py:74, 118-122`），`pending_calls` 改用工具调用 `id` 做键（`app/services/chat.py:107-110`），并在 `ToolMessage` 到达时清空累积状态（`app/services/chat.py:149-150`）。

**代码位置**
`app/services/chat.py:66-182`；回归测试 `tests/test_chat_events.py:124-161`

**动手验证**

```bash
# 1) 两条回归用例应当通过
cd car-care-ai
.venv/Scripts/python.exe -m pytest tests/test_chat_events.py -q

# 2) 亲手把坑二放回去，看用例真的会失败（这才是「真回归保护」的证明）
#    把 app/services/chat.py:109 的
#        key = tc.get("id") or f"idx{index}"
#    改成
#        key = f"idx{index}"
#    再跑一次：test_narration_is_reset_on_every_tool_round 应当失败
#    验完记得改回来（git diff 确认）
```

**常见误解**
- ❌「模型说的话不用管，最后拿 `done` 的完整正文就行」——`done` 的正文就是**由这些 delta 拼出来的**（`app/services/chat.py:155-156`），拼错了 `done` 就是错的。Java 侧也做了兜底：`done` 带正文时以 `done` 为准（`AiChatService.java:133-142`）。
- ❌「index 是这次请求里唯一的」——只在**单轮**内唯一。这是整个模块最容易记错的一条。

### 4.2 工具返回值是「给模型看的自然语言」，不是给前端的 JSON

**原理**
工具函数返回的字符串有两个消费者，优先级完全不同：

1. **主要消费者是模型**——它要读这段文字来决定怎么回答用户；
2. 前端只通过 `tool_end.toolSummary` 看一行摘要（`app/services/chat.py:199-204` 会把它压到 120 字）。

所以返回值刻意**不是**结构化 JSON，而是紧凑的中文短文本（`app/agent/tools/_common.py:15-21`）：

```
门店查询失败：车管家数据服务暂时不可用。请如实告诉用户暂时查不到这项信息、建议稍后再试，禁止编造任何数据。
```

这句话里「**请如实告诉用户…禁止编造**」是写给模型的行为指令——**工具返回值也是提示词的一部分**。

**更关键的一层：必须区分「查不到」和「查询挂了」**（`app/clients/carcare.py:26-40` 的 `ToolResult`）：

| 情况 | `ToolResult` | 对用户该怎么说 |
|---|---|---|
| 确实没有符合条件的门店 | `ok=True, data=[]` | 「杭州暂时没有营业中的门店」 |
| 上游接口超时/500 | `ok=False, error="…"` | 「查询服务暂时不可用，请稍后再试」 |

**如果把这两者混成一个空列表，模型就会把「服务挂了」说成「没有门店」**——用户得到的是一个听起来确定、但完全错误的答案。

**代码位置**
`app/clients/carcare.py:26-80`、`app/agent/tools/_common.py`、判定逻辑 `app/services/chat.py:194-196`

**动手验证**

```bash
# 把 Java 服务停掉，再问一个需要查门店的问题，观察 AI 的回答措辞
python document/tools/sse_probe.py -q "杭州有哪些门店？"
# 预期：tool_end 的 ok=false，摘要里是「失败」，最终回答是「暂时查不到/稍后再试」
# 而不是「杭州没有门店」
```

**常见误解**
- ❌「工具返回值应该返回 JSON，方便程序处理」——那是 REST API 的思路。这里的消费者是模型，自然语言更省 token 也更好理解。
- ⚠️ **一个诚实的技术债**：`_looks_failed()` 是靠**中文关键词**（`"失败"`、`"暂时不可用"`）判断成败的（`app/services/chat.py:194-196`）。它脆——改一句失败文案就可能让它误判。更稳的做法是让工具返回结构化的 `(ok, text)`。这是本项目明确承认的简化。

### 4.3 RAG 的切分与前缀

**原理**
见 3.8。要补的一点是**切分粒度怎么定的**：`CHUNK_SIZE=350` / `CHUNK_OVERLAP=60`（`app/rag/ingest.py:25-34`）。

- 太小：一条完整规则被拆散，检索到半句话反而误导模型；
- 太大：一个分片混进多个主题，向量被稀释，检索精度下降；
- `overlap=60`：相邻分片重叠一段，避免「关键句正好在边界上被切断」。

`MIN_SECTION_CHARS=30` 会丢掉过短的章节（通常是只有标题没内容的占位节）。

**代码位置**
`app/rag/ingest.py:29-34, 46-64, 84-87`；重建入口 `app/rag/store.py:92-115`

**动手验证**

```bash
# 1) 看语料被切成了什么（含前缀）
.venv/Scripts/python.exe -c "
from app.rag.ingest import load_chunks
cs = load_chunks()
print('分片数:', len(cs))
for c in cs[:3]:
    print('---'); print(c.page_content[:120]); print('meta:', c.metadata)
"
# 预期：分片数 30；每片开头是【文档名·章节名】

# 2) 只检索不生成，直接看命中与分数（排障第一步）
curl -s "http://127.0.0.1:8000/v1/rag/search?q=%E5%88%B9%E8%BD%A6%E7%89%87%E5%A4%9A%E4%B9%85%E6%8D%A2&k=5" \
  -H "X-Internal-Token: <你的令牌>"
```

**常见误解**
- ❌「切分就是个技术细节，随便切」——切分直接决定检索上限。检索不到，后面提示词写得再好也没用。
- ❌「检索到的资料越多越好」——`top_k=4` 是刻意压小的。塞 10 条不相关的资料进 prompt，既贵又会让模型被噪声带偏。

### 4.4 阈值标定：0.35 是怎么来的

**原理**
见 3.9。要强调的是**标定方法**，这比记住 0.35 这个数字重要得多：

1. 准备一批**相关问题**和**无关问题**；
2. 用 `/v1/rag/search` 逐条看命中分数；
3. 找出两簇的分布区间；
4. 阈值取两簇之间的**空档**；
5. **记下反例**（本次是「安心保返修政策」0.428），确保阈值不会把它滤掉。

**代码位置**
`app/config.py:39-42`（注释里写了完整的实测区间）、`app/rag/store.py:75-89`

**动手验证**

```bash
# 1) 不用起服务，直接看分数（最省事的标定方式；每次查询消耗一次 embedding 调用）
cd car-care-ai && .venv/Scripts/python.exe -c "
from app.rag import store
for q in ['刹车片多久换一次', '安心保的返修政策是什么', '今天天气怎么样']:
    hits = store.search(q, 4, 0.0)   # 阈值传 0.0，把命中的全部打出来看分布
    print(f'q={q!r}')
    for d, s in hits:
        print(f'   {s:.4f}  {d.metadata.get(\"section\")}')
    print()
"
# 实测输出（2026-09-24）：
#   刹车片多久换一次       → 0.6217 刹车片 / 0.6212 刹车盘 / 0.5869 刹车油 / 0.4811 轮胎
#   安心保的返修政策是什么 → 0.4651「安心保」施工质量承诺 / 其余骤降到 0.24 以下
#   今天天气怎么样         → 最高 0.2471，全在噪声区
# 结论：相关 0.46~0.62、无关 0.19~0.25，0.35 落在空档里；且「安心保」那条
#       靠 0.4651 才过线 —— 阈值设 0.5 以上它就会被滤掉。

# 2) 端到端对比：把阈值调到 0.6（照搬别的项目的值），重启服务再问同一个问题
# .env: RAG_MIN_SCORE=0.6
python document/tools/sse_probe.py -q "安心保的返修政策是什么？"
# 预期：检索被滤空 → 模型说不知道/给一般性建议，而这条答案其实在知识库里
# 改回 0.35 再问一次，对比差异 —— 这就是「阈值必须自己标定」的实证
```

**常见误解**
- ❌「0.35 是通用经验值」——不是。它绑定**这个 embedding 模型 + 这批语料**。换 bge-large 或大改语料，必须重新标定。
- ❌「阈值越高越安全」——越高越容易把正确答案滤掉（见上面的反例）。

### 4.5 无状态 Agent 与请求级上下文

**原理**
见 3.10。要补的是**它为什么和并发能力直接相关**：Agent 实例里没有任何请求态，所以 100 个并发请求共用同一个实例也不会互相污染。反过来，只要有一处把状态写进实例字段（比如练习项目里的 `self.messages`），并发下必然串会话。

`AgentContext` 还有一个作用：**它就是 token/耗时统计的载体**（`app/agent/context.py:24-29`）。`TimingMiddleware` 把每次模型调用的耗时和 token 累加进去，最后由 `done` 事件带回 Java 落库。这样「一次对话花了多少钱」是可查的，不用靠压测总耗时反推。

**代码位置**
`app/agent/context.py`、`app/agent/builder.py:45-66`、`app/agent/middleware.py:59-79`、`app/services/chat.py:68`

**动手验证**

```bash
# 一次带工具的对话，观察 Python 日志里每次模型调用的耗时与 token
python document/tools/sse_probe.py -q "杭州有哪些门店？小保养多少钱？"
# 日志预期形如：
#   模型调用 #1 耗时 1234ms tokens=812/56
#   模型调用 #2 耗时 987ms  tokens=1024/48
# done 帧里 latencyMs / inputTokens / outputTokens 是各次调用的累计
```

**常见误解**
- ❌「每次请求新建 Agent 更安全」——构建 Agent 要初始化模型客户端，每请求一次是浪费；无状态设计就是为了避免这么做。
- ❌「token 数只影响账单」——它还直接决定上下文长度上限。`AiContextService` 把车辆限 5 台、订单限 5 笔、每笔明细限 3 个，就是为了不让 prompt 膨胀（`AiContextService.java:43-45`）。

### 4.6 SSE 跨语言透传与取消传导

**原理**
链路是：`Python SSE` → `Java WebClient 解码成 Flux<String>` → `累积副作用` → `逐帧写回前端`。

三个关键点：

1. **只发裸 JSON**（见 3.4 的二次包装坑）；
2. **副作用靠解析帧累积，不另维护状态**——正文、工具轨迹、token 都从透传的帧里顺手收集（`AiChatService.java:120-147`），避免「转发的内容」和「落库的内容」不一致；
3. **取消自动传导**：客户端断连 → Reactor 取消订阅 → WebClient 中止上游请求 → Python 收到 `CancelledError` → 停止模型生成。整条链路不用手写取消逻辑（`app/services/chat.py:173-176` 只做日志留痕）。

**为什么落库必须异步**：JDBC 是阻塞的，Reactor 的事件循环线程数 = CPU 核数，阻塞一个就少一个。所以落库丢到 `aiDbExecutor`（核心 2 / 最大 8 / 队列 200 / **CallerRunsPolicy**）。队列满时由调用线程自己跑，**天然形成背压**，不会无限堆积把内存吃满（`AiWebClientConfig.java:72-92`）。

`doFinally` 的选择也很关键：它在**完成、出错、客户端断开**三种情况下都会执行，保证「用户问过的问题一定留痕」（`AiChatService.java:113-115`）。

**代码位置**
`AiChatService.java:66-118, 172-212`、`AiClient.java:47-69`、`AiWebClientConfig.java:43-92`、`app/services/chat.py:173-176`

**动手验证**

```bash
# 1) 看 Java 网关推给前端的原始帧（-N 关闭 curl 缓冲，才能看到逐帧到达）
TOKEN=$(curl -s -X POST http://127.0.0.1:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"123456"}' | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
curl -N -X POST http://127.0.0.1:8082/api/ai/chat \
  -H "token: $TOKEN" -H "Content-Type: application/json;charset=UTF-8" \
  -d '{"message":"你好，你能做什么？"}'
# 预期：每帧形如 data: {"type":"delta",...}，且是**一个个出现**而不是最后一起出现

# 2) 验证断连会传导：发起上面的请求后 1 秒按 Ctrl+C
#    Python 日志应出现「客户端断开，取消对话 session=...」
#    且不再有新的「模型调用 #N」日志（说明生成真的停了）

# 3) 验证落库不丢：压测后核对会话数与消息数（见 performance-report.md 第六节）
```

**常见误解**
- ❌「客户端断了，后端跑到自然结束也无所谓」——那是**在烧钱**。一次带工具的对话要调 2~3 次模型，用户走了还在跑就是纯浪费。
- ❌「180s 是这次对话的总时长上限」——是**空闲**超时。搞错了会在模型长时间思考时误杀正常请求。

### 4.7 用户上下文裁剪与「越权无法表达」

**原理**
见 3.12。这里补**裁剪的两个理由**：

1. **成本与延迟**：全量下发会把 prompt 撑到几万 token，既慢又贵，而模型只需要「近期上下文」；
2. **最小暴露面**：订单明细只带项目名和金额，不带单价、图片等模型用不到的信息（`AiContextService.java:162-171`）。

`resolveCity` 的实现很能体现工程判断：用户表**没有城市字段**，与其加一列，不如用「最近一笔订单的门店城市」推断（`AiContextService.java:244-264`）——车主最常去的门店城市基本就是他的活动城市，用来做门店推荐的默认范围足够准。**用已有信号代替新增字段**。

会话归属校验统一收在 `resolveSessionId` / `mustOwn` 两个方法里，而不是散在各个调用点，避免漏掉某个接口造成越权读他人对话（`AiConversationService.java:45-65, 77-85`）。

**代码位置**
`AiContextService.java:37-105, 244-264`、`AiConversationService.java:45-85`、`app/schemas.py:77-90`

**动手验证**

```bash
# 越权路径实测（用两个账号交叉验证）
# 1) 用 zhangsan 登录拿到自己的 sessionId
# 2) 用 lisi 的 token 去读 zhangsan 的会话消息
curl -s "http://127.0.0.1:8082/api/ai/conversations/<zhangsan的sessionId>/messages" \
  -H "token: <lisi的token>"
# 预期：{"code":0,"msg":"会话不存在或无权访问"}（不是返回别人的聊天记录）
# 3) 用 lisi 的 token 带 zhangsan 的 sessionId 发消息，同样应被拦
```

**常见误解**
- ❌「AI 服务直连 MySQL 更简单」——那就把「营业中/在售」的过滤口径复制成了两份，两处必然漂移；同时 AI 服务要持有数据库凭证，出事时爆炸半径更大（`app/clients/carcare.py:1-10` 有完整说明）。
- ❌「我在提示词里告诉模型不要泄露别人的数据就够了」——提示词是**软约束**，模型可以被诱导绕过。安全必须靠**结构**，不能靠模型自觉。

### 4.8 工具失败与空结果的语义区分

**原理**
见 4.2 的核心表。这里补一个**本项目的真实修复案例**：预约草稿工具 `build_booking_draft` 会校验「用户要预约的项目是否真的属于这家门店」（`app/agent/tools/booking.py:44-56`）。

如果用户说「约西湖文一店的大保养」，而这家店其实没有这个项目，工具会返回：

```
生成草稿失败：项目 [7] 不属于门店 1 或在售状态已变更。请重新用 search_service_items 确认该门店的在售项目，不要凭印象编造项目。
```

**这是「把校验放在工具里」而不是「指望模型别编」**——模型可能从上文里记住一个别家店的项目 ID 就填进来了，工具必须自己挡住。

**代码位置**
`app/agent/tools/booking.py:44-56`、`tests/test_tools.py`

**动手验证**

```bash
.venv/Scripts/python.exe -m pytest tests/test_tools.py -q
# 两条重点用例：
#   - 项目不属于该门店必须拒绝
#   - 上游接口挂了必须如实说而不是编数据
```

**常见误解**
- ❌「模型很聪明，不会填错参数」——它填参数靠的是**概率**。凡是「填错会写脏数据」的地方，工具必须自己校验，不能把正确性寄托在模型上。
- ❌「工具报错应该抛异常让上层统一处理」——工具异常会被框架转成错误消息塞回给模型，模型很可能顺势编一个答案。**返回明确的失败文案，并要求它如实转述**，才是可控的行为（`app/agent/tools/_common.py:15-17`）。
- ❌「校验时先取一批数据再比对就行」——**校验必须点查，不能拿分页结果比对**。这个坑真实发生过：`build_booking_draft` 早先用 `search_items(store_id=...)`（默认 `limit=10`、按 id 升序）取回前 10 条再比对 `item_ids`，门店项目一旦超过 10 个，第 11 个之后的**合法项目会被判成「不属于该门店」**。更糟的是错误提示把两种情况合并成一句「不属于该门店或在售状态已变更」，模型无法判断该换项目还是换门店，于是从自己看得见的项目里**换了一个顶上**——用户要前刹车，草稿里却是列表第一个的小保养。
  - 正确做法：Java 内部接口加 `ids` 点查（不分页、且**不按 storeId 过滤**），Python 侧 `search_items(ids=[...])`，再在工具里分别判断「查不到」与「`storeId` 不等于目标门店」，给出两条不同的提示。
  - 顺带一提，「不按 storeId 过滤」是刻意的：如果点查也带上 storeId，跨门店的项目会**静默消失**，就只剩「查不到」一种解释，错误提示依然会误导模型。

### 4.9 「不调工具却报成功」：提示词里的话术就是幻觉的模板

**原理**

这是本项目最值得记的一个 prompt 教训。提示词原本写着：

```
4. 你只能生成「预约草稿」，不能替用户下单。生成草稿后要说明：需要用户点击卡片上的确认按钮才会真正下单。
```

看起来只是约束语气，实际效果是**给了模型一句可以照抄的成品话术**。模型于是常常直接写出「帮你生成预约草稿……点卡片上的确认预约即可」，而**根本没有调用 `build_booking_draft`**——用户照着这句话去找卡片，什么也找不到。实测（deepseek-chat，同一句话跑 8 次）**8 次里出现 4 次**。

**怎么修**（两层，缺一不可）：

1. **提示词层**：把那句话改成「**调用工具才算生成**」的硬契约，并明确禁止在调用成功前描述草稿内容或提「卡片」二字。改写后同样 8 次**全部**正确。
2. **代码层兜底**：提示词只是概率约束，不是保证。所以 `stream_chat` 在收尾时做一次核对——「回答里在宣称草稿已生成」而「本次并没有产出 draft 事件」，就补一句更正并记 warning（`app/services/chat.py` 的 `_claims_draft`）。

```python
if not draft_ready and _claims_draft(answer):
    log.warning("模型声称已生成预约草稿但未调用工具 session=%s", req.sessionId)
    yield ChatEvent(type="delta", content="\n\n（更正：这次其实没有生成预约卡片……）")
```

注意更正是**追加一个 `delta`**：正文已经流式发出去了，改不了历史，只能补一句，并且要把它一起算进 `done.content`（否则落库的文本和用户看到的不一致）。

**动手验证**

```bash
.venv/Scripts/python.exe -m pytest tests/test_chat_events.py -q -k draft
# 4 条重点用例：
#   - 宣称草稿但没调工具 → 必须补更正
#   - 只是问「要不要生成草稿？」→ 不该误判（末尾问句跳过）
#   - 如实说「无法生成草稿」→ 不该误判（否定词跳过）
#   - 草稿 JSON 坏了但模型说「已生成」→ 同样要更正
```

**常见误解**
- ❌「提示词写清楚就行」——凡是「模型说了但实际没做」的后果由用户承担，就不能只靠提示词，必须有一道能观测到事实的代码兜底。
- ❌「关键词判断太土，应该用模型判断」——这里的关键词判断是**宁可漏判也不误判**（末尾问句、否定词都跳过）。用第二次模型调用去做这件事，成本和延迟都不划算，而且它自己也会错。
- ❌「更正直接改 `done.content` 就行」——前端渲染的是 `delta` 流，只改 `done` 用户看不到。

---

## 五、阅读代码的推荐顺序

**不要从 `main.py` 开始读**——你会看到一堆路由定义然后迷路。按这个顺序，每一步只解决一个问题：

```
Python 侧（car-care-ai/）
1. app/schemas.py                 ← 先看契约：Java 下发什么、SSE 吐什么，一张表看全
2. app/security.py                ← 只有 35 行，最短最纯粹，先理解「未配置即拒绝」
3. app/config.py                  ← 所有可调参数与默认值集中在一处（含阈值标定注释）
4. app/agent/prompts.py           ← 系统提示词：项目对模型的行为约束全在这里
5. app/agent/tools/_common.py     ← 工具返回值的风格约定（给模型看的自然语言）
6. app/agent/tools/booking.py     ← 一个完整的工具长什么样（含参数校验与草稿标记）
7. app/clients/carcare.py         ← ToolResult 两态：查不到 vs 查询挂了
8. app/rag/ingest.py → store.py   ← RAG 的切分与检索，顺序不要反
9. app/agent/builder.py           ← Agent 怎么组装（工具/提示词/中间件/上下文）
10. app/agent/middleware.py       ← 日期注入与 token 统计
11. app/services/chat.py          ← 最核心也最难：消息流 → SSE 事件（读两遍）
12. app/main.py                   ← 最后看入口，此时路由只是收尾

Java 侧（car-care-server/）
13. config/AiProperties.java      ← 先看配置项与超时预算注释
14. config/AiWebClientConfig.java ← WebClient 与落库线程池（背压设计在这里）
15. client/AiClient.java          ← SSE 解码成 Flux + 异常转译
16. service/AiContextService.java ← 用户数据怎么裁剪下发（越权设计的落点）
17. service/AiConversationService.java ← 会话归属校验与历史修剪
18. service/AiChatService.java    ← 透传 + 副作用累积 + 落库（读两遍）
19. controller/AiController.java + AiInternalController.java
```

**读法**：本项目每个文件都有中文模块 docstring，**先说清楚「这个文件解决什么问题、踩过什么坑」**。先读 docstring，再读方法，最后对照调用链。

`app/services/chat.py` 和 `AiChatService.java` 值得读两遍：第一遍看流程，第二遍专门找「哪里在处理异常和边界」。

---

## 六、动手实验清单

**这 10 个实验是这份文档最有价值的部分。** 你有现成的运行环境——改一个参数、跑一次、看到日志和帧的变化，比读十遍文档都管用。

| # | 实验 | 改什么 / 怎么跑 | 预期现象 | 学到的 |
|---|---|---|---|---|
| 1 | 装上「显微镜」 | `python document/tools/sse_probe.py -q "杭州有哪些门店？"`，加 `--raw` 看原始帧 | 逐帧打印 start/tool_start/tool_end/delta/done | SSE 事件契约长什么样 |
| 2 | 让检索「消失」 | `.env` 里 `RAG_ENABLED=false` 重启 | `/healthz` 的 `ragReady:false`；问保养周期时模型答得更含糊、可能拒答 | RAG 到底贡献了什么 |
| 3 | 阈值标定实证 | `RAG_MIN_SCORE` 0.35 → 0.6 → 0.2 | 0.6 时「安心保」类问题检索被滤空；0.2 时闲聊也会命中噪声 | 阈值必须自己标定 |
| 4 | 直接看检索分数 | `curl /v1/rag/search?q=...&k=5` | 看到每条的 score，自己找相关/无关的分界 | 排障第一步永远是先看检索 |
| 5 | 把预算掐到极限 | `.env` 里 `MAX_TOOL_ROUNDS=1` 重启，问一个需要多步的问题 | 工具跑了一两次就被截断，回答变成兜底文案（「我查到了一些信息…」） | 中间件限流与兜底收尾 |
| 6 | 日期注入对比 | 问「这周六上午去保养，帮我约一下」 | 草稿里的日期是**基于今天**算出来的（不再是编的 2026-06-27） | dynamic_prompt 的作用 |
| 7 | 降级验证 | 把 Python 服务停掉 | `/api/ai/health` → `available:false`；`/api/ai/chat` → 友好 error 帧；`/api/orders/my` 等主链路**完全正常** | 降级设计 |
| 8 | 令牌不一致 | 把 `.env` 的 `CARCARE_INTERNAL_TOKEN` 改一个字符重启 | 所有内部接口 401；再把它留空 → 服务直接 500 拒绝一切 | 「未配置即拒绝」的安全默认值 |
| 9 | 亲手把 bug 放回去 | 把 `chat.py:109` 的 `key` 改成 `f"idx{index}"` | `pytest tests/test_chat_events.py` 里多轮 reset 那条**失败** | 回归测试是真保护，不是摆设 |
| 10 | 并发下看工具链路 | 用 `document/jmeter/ai-chat-test.jmx` 打 20 并发带工具的问题 | 0 错误；Python 日志里模型调用耗时上涨但工具回调正常 | 并发下的瓶颈在上游模型 |

### 实验的通用方法

和 `LEARNING.md` 一样，每个实验都遵循同一套套路：

```
1. 先记下当前值   → 知道「改之前是什么样」
2. 只改一个变量   → 不要同时改阈值和轮数
3. 观察现象       → 对不上就停下来查为什么，别跳过
4. 恢复原值       → 保持环境干净（.env 改完记得改回来）
5. 写下判断       → 这个实验证明/否证了什么
```

**最重要的一条：只改一个变量。** 同时改 `RAG_MIN_SCORE` 和 `RAG_TOP_K`，看到现象你分不清是哪个引起的。

### 本模块专属的观察工具

```bash
# 1) SSE 排障脚本：直接看每一帧（支持 -s 续会话、--raw 看原始帧）
python document/tools/sse_probe.py -q "我的车该保养了吗？"
python document/tools/sse_probe.py -q "继续" -s <上次打印的 sessionId>

# 2) 直连 Python（绕过 Java）的并发对照脚本
python document/tools/ai_direct_load.py -n 20 -q "杭州有哪些门店？"

# 3) 看 Python 日志里每次模型调用的耗时/token —— 判断「慢在哪」最直接的证据
#    形如：模型调用 #2 耗时 2597ms tokens=1523/87

# 4) 回归测试（不联网、不依赖 Java）
cd car-care-ai && .venv/Scripts/python.exe -m pytest tests -q     # 33 passed
```

> **Windows 上的两个坑**（本项目实测踩过）：
> - Git Bash 里跑 JMeter 时 `-Jpath=/v1/chat` 会被 MSYS 自动改写成 `D:/study_xue/Git/v1/chat`，请求 404 且**不报参数错误**。要加 `MSYS_NO_PATHCONV=1`。
> - 判测试结果、判错误日志**一律用 Python 读**，不要用 `grep`：这个环境的 `grep` 是 shell 函数，读混编码（GBK 污染）的日志文件会按二进制处理并**静默返回空**，让你误判「全通过」。

---

## 七、延伸学习路径

按「投入产出比」排序，**先做前面的**：

### 第一梯队（直接提升，立刻能用）

1. **LangChain 1.x 官方文档的 Middleware 与 Tools 两章**——本项目的 `@tool` / `@dynamic_prompt` / `@wrap_model_call` / `context_schema` 全在这里，比任何博客准确
2. **OpenAI Function Calling 规范**——理解「模型只吐 JSON」这件事的标准形态，DeepSeek/Claude 都兼容这套
3. **SSE 规范（`text/event-stream`）**——只有几页，看完就彻底明白 `data:` 前缀和空行分隔是谁加的
4. **自己重跑一遍实验 3（阈值标定）并写下结论**——这是最能体现「你真的做过 RAG」的动作

### 第二梯队（理解更深）

5. **余弦相似度与向量检索的直觉**——为什么「语义相近」能用距离衡量，以及它的失效场景（否定句、长文本稀释）
6. **Prompt Engineering 的系统化方法**——重点看「用场景清单代替强制指令」为什么能减少幻觉调用
7. **LangGraph 的图执行模型**——理解 `langgraph_node`、checkpointer、中断与恢复。本项目刻意**不用** checkpointer 做持久化（会话存在 Java 的 MySQL 里），但要知道另一种选择长什么样
8. **流式协议对比**——SSE vs WebSocket vs chunked HTTP，各自适合什么场景

### 第三梯队（往上走）

9. **Agent 的记忆与上下文管理**——多轮对话里怎么控制 token 线性上涨（本项目承认的不足：没有跨轮工具结果复用）
10. **RAG 进阶：重排序（rerank）、混合检索（BM25 + 向量）、查询改写**——本项目只做了最基础的一路向量检索
11. **LLM 应用的可观测性**——把 token/耗时/工具轨迹按用户和按天汇总（本项目只落库，没汇总）

### 第四梯队（工程素养）

12. **怎么把「概率性组件」关进笼子**——工具做参数校验、失败要显式、降级要有开关，这三条比调提示词更重要
13. **怎么写 AI 模块的测试**——本项目 `tests/test_chat_events.py` 的思路值得学：**用脚本化的假 Agent 替掉真模型**，把不确定的输出变成确定的输入，这样事件归一化逻辑才能被稳定地测

---

## 八、自检清单

**面试前过一遍这个，能全答上来才算真懂：**

- [ ] 能说清「模型不会执行函数，它只吐 JSON」，以及工具 schema 是从哪来的
- [ ] 能画出 Agent 循环，并说出三个停止条件分别由谁保证
- [ ] 能解释为什么 `ModelCallLimitMiddleware` 的 `run_limit` 是 `max_tool_rounds + 1`
- [ ] 能说清 `stream_mode="messages"` 吐的二元组是什么，以及怎么区分「模型说话」和「工具返回」
- [ ] 能说出 `tool_call_chunks` 的 index **只在单轮内唯一**，以及用 index 做键会导致什么症状
- [ ] 能解释 `reset` 事件为什么存在，以及它换来了什么（真流式）、代价是什么（闪一下）
- [ ] 能说清 RAG 四步，以及为什么按 `##` 切而不是按字数切
- [ ] 能说出分片前缀为什么参与向量化（举「刹车片」的例子）
- [ ] 能说出 0.35 这个阈值是怎么标定的，以及那个反例（安心保 0.428）
- [ ] 能解释「查不到」和「查询挂了」为什么必须分成两种返回，混在一起会出什么事故
- [ ] 能说清 `AgentContext` 为什么让 Agent 可以并发复用，对比实例字段存 messages 会怎样
- [ ] 能说出 `dated_system_prompt` 为什么不能写死在提示词常量里
- [ ] 能解释「用户私有数据由 Java 下发」为什么让越权**在结构上不成立**，而不是「检查过了」
- [ ] 能说清 Spring 对 `Flux<String>` 的二次包装坑（为什么只能发裸 JSON）
- [ ] 能解释 180s 是**空闲**超时而不是总时长，以及总预算为什么放在 Python 侧
- [ ] 能说清客户端断连后取消是怎么一路传导到模型生成的
- [ ] 能说出落库为什么必须异步、`CallerRunsPolicy` 起什么作用、为什么用 `doFinally`
- [ ] 能说出三个降级路径，并证明 AI 挂了不影响下单支付主链路
- [ ] 能说出本项目**明确承认的不足**：`_looks_failed` 靠中文关键词判成败、没有 token 成本汇总、没有跨轮工具结果复用、并发上限未知

**答不上来的，回去做对应的实验。** 尤其第 5、7、9、10 条——它们只存在于运行时，读代码看不出来，必须跑起来看帧和日志。
