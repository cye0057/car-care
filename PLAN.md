# 汽车维修保养服务系统 —— 实施计划书

> 状态：✅ 全部完成 ｜ 创建：2026-09-14 ｜ 每阶段完成后更新文末「进度记录」

## 总体思路

以「维修保养」为核心业务域，构建一个具备高并发场景的本地车主服务平台。基础功能先跑通，再分阶段叠加缓存、消息、Feed 流等高价值技术点，每个技术点都做「方案对比 + 改进 + JMeter 压测数据」，形成可量化的优化闭环。

## 项目结构

```
D:/study_xue/JAVA_code/car-care/
├── PLAN.md              # 本计划书（随项目版本管理）
├── car-care-server/     # Spring Boot 3.5 + JDK17 单模块后端
├── car-care-admin/      # Vue3 + Vite + Element Plus 管理后台（先做）
├── car-care-app/        # 车主用户端 H5（阶段 5 再做）
└── document/sql/        # 建表 SQL + 种子数据
```

后端包结构：`com.carcare` 下 `config / common / controller / service / mapper / entity / dto / vo / utils`。

## 技术栈

- 后端：Spring Boot 3.5.x、JDK 17、MyBatis-Plus 3.5.x、MySQL 8、Redis（spring-data-redis + Redisson）、JJWT + HandlerInterceptor + ThreadLocal 登录鉴权、springdoc-openapi、Hutool、Lombok、阿里云 OSS SDK（图片上传）、支付宝开放平台 SDK（沙箱支付）
- 前端：Vue 3 + Vite + Element Plus + Pinia + axios 封装
- 压测：JMeter 5.6.3
- 消息队列：RabbitMQ（阶段 2 引入）

## 数据库设计（car_care 库）

| 表 | 说明 | 承载的技术点 |
|---|---|---|
| `t_user` | 车主/管理员（role 区分） | 登录拦截、ThreadLocal |
| `t_store` | 维修门店：地址、经纬度、评分、状态 | 缓存三件套、GEO 附近门店 |
| `t_service_category` / `t_service_item` | 保养项目：换机油、四轮定位… | 缓存一致性、上下架状态机 |
| `t_package` / `t_package_item` | 小/大保养套餐与明细快照 | 套餐与项目联动约束 |
| `t_coupon` | 优惠券：库存、有效期、券类型 | 秒杀、Redis+Lua |
| `t_coupon_order` | 领券记录，唯一约束防重复领 | 幂等、MQ 异步落库 |
| `t_order` / `t_order_detail` | 订单状态机：待支付→已支付→施工中→已完工→已评价/已取消 | 分布式ID(号段)、延迟消息关单 |
| `t_work_order` | 维修工单：技师、进度、完工图片 | WebSocket 来单提醒 |
| `t_vehicle` | 车辆档案：车牌、车型、里程、下次保养提醒 | 定时任务保养提醒 |
| `t_review` | 门店评价/养车笔记 + 点赞 | Feed 流 |
| `t_follow` | 关注关系 | 推模式 Feed |

## 阶段规划

| 阶段 | 内容 | 状态 |
|---|---|---|
| **0 基础框架** | SQL 建库 + 后端骨架 + 核心 CRUD 纵向切片 + 管理后台骨架 | ✅ 完成 |
| **1 缓存体系** | 门店/首页缓存；穿透(空值)/击穿(互斥锁+逻辑过期)/雪崩(随机TTL)；Cache Aside + 延迟双删；JMeter 压测前后对比 | ✅ 完成 |
| **2 优惠券秒杀** | Redis+Lua 预减库存、一人一券、号段订单号、Redisson 兜底；RabbitMQ 异步落库 + 消费幂等 + 死信延迟关单 | ✅ 完成 |
| **3 附近门店+热榜** | Redis GEO 查询、ZSet 热度排行 + Spring Task 定时刷新 | ✅ 完成 |
| **4 评价 Feed 流** | 关注/粉丝、推拉结合、滚动分页（最小 ID）、点赞 Set + 计数 | ✅ 完成 |
| **5 用户端+工单提醒** | car-care-app（Vant）、下单预约、WebSocket 来单提醒 + Redis Pub/Sub 集群转发 | ✅ 完成 |
| **6 打磨** | 全链路压测报告、README/架构图、总结文档 | ✅ 完成 |
| **7 上传+支付** | 阿里云 OSS 图片上传（门店/项目/笔记/头像）、支付宝沙箱支付（wap 下单+异步回调验签）、用户头像、移除管理台「版本路线」 | ✅ 完成 |

阶段 0 只实现「登录 + 门店/分类/项目/套餐/优惠券 CRUD + 订单查询/流转」接口，其余接口随各阶段填充，保证每阶段都是可运行状态。

## 技术改进点设计

- **订单号**：不用 UUID/自增，采用号段模式生成 + 业务前缀可追溯
- **超时关单**：不用定时任务轮询扫表，改用 RabbitMQ 延迟消息（死信队列）
- **缓存一致性**：不只删缓存，采用延迟双删 + 兜底重查
- **Feed**：按门店/达人体量做推拉结合，而非单一推模式
- 每项改进配压测数据（QPS/RT/命中率前后对比）

## 进度记录

- 2026-09-14：计划书落盘；环境确认（JDK17/21、Maven 3.9.11、Node 24、Redis 8.8 运行中、MySQL 8.0、JMeter 5.6.3）
- 2026-09-14：**阶段 0 完成**。car_care 库 14 表 + 种子数据导入；后端 8081 启动（MyBatis-Plus 分页、JWT+拦截器鉴权、订单状态机 1→5/2→3/3→4/4→6 校验生效、套餐-项目启售联动约束）；前端 5173（登录/工作台/门店/分类/项目/优惠券/订单 6 页面 + vite /api 代理验证通过）。
  - 运行方式：后端 `cd car-care-server && mvn spring-boot:run`；前端 `cd car-care-admin && npm run dev`；访问 http://localhost:5173，账号 admin / 123456
  - 注意事项：本机 curl 若走 7890 代理需加 `--noproxy '*'`；MySQL 服务名是 `MySQL`（不是 `MySQL80`——后者是残留未使用服务），开机未自启时用管理员 `net start MySQL`
- 2026-09-14：**代码可读性专项完成**。后端全部 40 个类补类级/方法级 Javadoc（实体说明业务语义与演进挂点、Service 说明约束规则、状态机流转表逐行注释）；Controller/DTO/VO 加 springdoc `@Tag/@Operation/@Schema` 注解，在线文档按 01-认证~08-工作台 分组；编译与接口回归通过，swagger-ui 可访问。
- 2026-09-14：**前端 UI 重设计完成（见 car-care-admin/DESIGN.md）**。落地设计令牌（tokens.css，8px 间距体系/双主题色板/两级阴影，映射 --el-* 变量）、深色模式切换（useTheme composable + localStorage 持久化）、布局升级（可折叠侧栏/移动端抽屉/面包屑/头像下拉/路由转场）、登录页品牌化（左品牌区+右卡片，移动端自适应）、工作台重构（统计卡+最近订单+快捷入口）。验证：全部 12 个模块经 Vite 按需编译 200。
- 2026-09-14：**修复路由切换白屏 bug**。根因：`<transition mode="out-in">` 的离场回调在部分环境不触发，导致 router-view 内容区卡在 leave 状态空白。方案：弃用 Vue transition 组件，改为纯 CSS `@keyframes` 进场动画（.page-enter），不依赖 JS 回调；浏览器实测 5 段连续路由切换均正常渲染。同时按反馈将侧栏回滚至简洁版结构，全站配色由蓝色系换为青绿（teal #0d9488）主色，登录页/统计卡/侧栏渐变同步。
- 2026-09-14：**阶段 1 缓存体系完成**。后端端口迁至 8082（8081 被本机另一项目占用）。实现：CacheHelper（SETNX 互斥锁 + Lua 安全解锁 + 500ms 延迟双删）、门店详情逻辑过期缓存（CacheData 内嵌 expireAt，物理 TTL 7 天兜底）、空值缓存防穿透（120s）、列表 TTL+随机抖动防雪崩、写路径 Cache Aside+双删、cache.enabled 开关用于基线对比。Redis 序列化改用带 @class 多态类型 + JavaTimeModule 的 ObjectMapper。
  - **JMeter 压测数据**（本机 MySQL 主键查询本身 <1ms，故延迟差异小，核心价值在 DB 读保护）：
    | 场景 | 请求数 | 关缓存 MySQL SELECT | 开缓存 MySQL SELECT | 降幅 |
    |---|---|---|---|---|
    | 热点详情（200并发×25） | 5000 | 5001 | 1 | 99.98% |
    | 穿透恶意id（200并发×25） | 5000 | 5000 | 1 | 99.98% |
    | 击穿：删key后200并发同打 | 200 | ~200 | 2 | 互斥锁生效 |
  - 压测脚本：document/jmeter/store-detail-cache.jmx（-Jthreads/-Jloops/-JreqPath 参数化）；结果 jtl 同目录
  - 简历条目（可直接用）：「针对门店详情读多写少场景设计 Redis 缓存：逻辑过期+互斥锁防击穿、空值缓存防穿透、TTL随机抖动防雪崩，写路径 Cache Aside+延迟双删保一致性；JMeter 压测 5000 请求下数据库查询从 5001 次降至 1 次，读放大降低 99.98%」
- 2026-09-15：**阶段 2a 秒杀核心完成**（2b RabbitMQ 待安装）。实现：Lua 原子脚本「查库存+查重复+预减+记名」单线程串行化防超卖；领券记录表唯一索引 (coupon_id,user_id,type) 第二道闸门；DB 条件扣减 `stock>0` 第三道闸门；落库失败补偿回滚 Redis。号段发号器采用双缓冲+90%预取版（SegmentService，seq 表 t_seq_alloc，步长100）。管理端新增秒杀发布/重置接口，用户端 /api/seckill/coupons/{id} 抢券 + 余量查询。
  - **秒杀压测数据**（30 用户×10 轮=300 并发抢 20 库存）：成功恰好 20、DB 剩余 0（无超卖）、领券记录 20 条且用户去重 20（无重复）、售罄后 Redis 库存 0 直接拦截不再触库；号段 id 1001~1041 趋势递增无冲突
  - 压测脚本：document/jmeter/seckill-test.jmx（CSV Data Set 注入 300 行用户 token，避免登录采样器干扰）
  - 踩坑记录：JMeter JSONPostProcessor 提取 token 失效返回 401 → 改预生成 token CSV；jtl 文件追加不覆盖，重跑前必须删除旧文件
  - 简历条目（草稿）：「设计优惠券秒杀链路：Redis+Lua 原子预减库存（库存判断/一人一券/扣减/记名四步合一），唯一索引+条件 UPDATE 双重兜底防超卖，落库失败自动补偿回滚；发号器采用号段模式（双缓冲+90%异步预取消除取号毛刺）；300 并发抢 20 库存压测 0 超卖 0 重复」
- 2026-09-15：**阶段 2b MQ 异步化完成**（RabbitMQ 3.13.7 已装于 D:\study_xue\RabbitMQ，管理台 15672 guest/guest）。实现：① 秒杀改异步——Lua 抢中后发号+投递消息立即返回领券记录 id，消费者手动 ack 落库（幂等：唯一索引冲突回补库存后吞掉；毒消息 nack 不重队）；② 死信延迟关单——下单投递 TTL 队列（演示 10s，生产 30min）→ 过期进死信 → 关单消费者按状态判断幂等（仅待支付才关）；③ 用户端下单/支付/我的订单接口，订单号 CC+日期+号段可追溯；④ 前端可轮询 /api/seckill/orders/{id} 查异步结果。
  - **终验数据**：冷启动 300 并发抢 20 库存 → accepted=20、落库=20、去重用户=20、DB 剩余 0；延迟关单双路径：未支付单 14s 后自动转「已取消(超时未支付，系统自动关闭)」，已支付单收到关单消息被状态判断跳过
  - **踩坑修复**：库存初始化原用互斥锁，未抢到锁的线程空手返回导致 Lua 把「key 不存在」误判售罄（压测实测丢 7 请求）→ 改 setIfAbsent 原子装载，冷启动并发验证 20/20 无丢失
  - 简历条目（升级合并版）：「秒杀链路 Redis+Lua 原子预减 + RabbitMQ 异步落库削峰，消费端手动 ack、唯一索引幂等、毒消息隔离；订单超时关闭用死信队列替代定时扫表（DB 零空转）；号段模式发号（双缓冲+90%预取）；冷启动 300 并发压测 0 超卖 0 重复 0 丢失」
- 2026-09-15：**阶段 3 完成**。① GEO 附近门店：启动 ApplicationRunner 全量装载营业门店坐标到 cache:store:geo，门店新增/改坐标/转休息/删除实时同步（geoAdd/geoRemove）；接口 /api/nearby/stores 按距离升序+半径过滤。② 热度榜：热度=近7天有效订单+评价数，HotStoreTask 每分钟 cron 聚合 DB 重建 cache:store:hot:zset，/api/nearby/hot 走 ZREVRANGE+阶段1门店缓存。
  - 验证数据：距离计算 7.02/8.42km 与 redis GEORADIUS 原值一致；5km 半径正确过滤；改门店坐标后 GEOPOS 即时更新且附近查询命中（0.11km）；新支付订单在下一分钟边界自动入榜（0→1）
  - 踩坑记录：Spring Data 的 GeoResult.getDistance() 单位随查询单位漂移导致距离恒为 0 → 改用返回坐标做 haversine 自算，单位确定可测试（面试可讲）
  - 简历条目（草稿）：「基于 Redis GEO（GeoHash+ZSet）实现附近门店检索，启动全量装载+写路径实时同步；门店热度榜由定时任务聚合订单/评价数据刷新 ZSet，查询 O(logN+M)，把实时聚合成本摊平为固定刷新」
- 2026-09-16：**阶段 4 Feed 流完成**。实现：① 关注/取关（t_follow 唯一索引幂等）；② 发帖推模式写扩散——非大账号发帖把 id+时间戳推入每个粉丝收件箱 ZSet（feed:inbox:{uid}），粉丝数超阈值（carcare.feed.push-follower-threshold，默认3000）自动切拉模式；③ Feed 查询=收件箱(推)+关注大账号(DB拉)按 id 降序合并去重，双游标滚动分页（maxId+beginTime，每页5条）；④ 点赞 Redis Set 明细去重+DB GREATEST 计数原子增减；⑤ 门店评价分页/我的笔记接口。新增 Follow/Review 实体、FollowService/ReviewService/FeedService、3 个 Controller（12-笔记/13-关注/14-Feed）。
  - 验证数据：推模式发帖后粉丝收件箱 ZSet 立即可见、Feed 正常返回；阈值调 0 重启后发帖收件箱不变（7→7）但笔记仍通过拉模式出现在 Feed —— 推拉双模式实测切换；分页三页翻到底 9..5 / 4,3 / 空，无重叠无丢失
  - 踩坑记录（两个都是好素材）：① 初版 offset+score 边界叠加导致第二页双重跳过（返回空）→ 改双游标边界包含+id 过滤去重；② 阈值切换后同一笔记同时出现在收件箱与拉取结果产生重复 → 合并层按 id 去重根治
  - 简历条目（草稿）：「设计关注 Feed 流推拉结合：小账号写扩散推入粉丝收件箱 ZSet，超阈值自动转拉模式，查询合并双游标(maxId+time)滚动分页规避深分页；点赞 Redis Set+原子计数；阈值切换场景实测双模式」
  - 环境备忘：本机重启后 MySQL/RabbitMQ 不自启，需 `net start MySQL`（管理员，服务名是 `MySQL` 而非残留的 `MySQL80`）+ 运行 `D:\study_xue\RabbitMQ\rabbitmq_server-3.13.7\sbin\rabbitmq-server.bat`；建议把两者注册为 Windows 服务开机自启
- 2026-09-16：**阶段 5-上 WebSocket 来单提醒完成**。链路：新订单创建/订单进入施工生成工单 → WsNotifyService 发布 JSON 到 Redis 频道 ws:notify → 各节点 RedisMessageListenerContainer 订阅 → WebSocketServer(@ServerEndpoint /ws) 按角色投递给在线管理员会话（集群下连接在哪个节点就哪个节点发，无需会话粘滞）。前端 Layout 铃铛+未读角标+通知面板+ElNotification 弹窗，30s 心跳+3s 断线重连，握手用 JWT 鉴权（非法 token 4001 拒绝）。
  - 验证：登录后端日志「WS 连接建立 userId=1 role=0」；触发新订单+工单 → 角标 2、面板两条中文通知文案正确
  - 踩坑记录（三个，均修复）：① ServerEndpointExporter 只注册 Spring Bean 的 @ServerEndpoint 类——漏标 @Component 导致 /ws 落 MVC 报 404 前端重连风暴；② 该 Bean 在 webEnvironment=NONE 的上下文测试里炸（ServerContainer not available）→ @ConditionalOnWebApplication(SERVLET) 隔离，用户写的 SegmentServiceTest 3/3 恢复通过；③ Redis 订阅端 new String(bytes) 用 Windows 默认 GBK 解码 UTF-8 中文 → 乱码，显式 StandardCharsets.UTF_8
  - 号段表加固：重跑建库脚本会丢手工插入的 order 行 → 已把 ('order',0,1000) 补进 phase2_seckill.sql 种子；SegmentService 对缺行抛带指引的 IllegalStateException
  - 简历条目（草稿）：「工单动态经 Redis Pub/Sub 扇出至各节点 WebSocket 会话，实现集群无关的来单实时提醒；握手 JWT 鉴权、心跳保活、断线重连；通知尽力而为+列表兜底的设计取舍」
- 下一步（阶段 5-下：车主端 H5 car-care-app）。后端接口已齐（登录/券/订单/Feed/门店浏览/评价/关注全部就绪），只剩新前端工程。**拆小步做，每轮一个页面、可独立验证**：
  - [x] 0 骨架：Vue3 + Vite + Vant4 + vue-router + axios（未用 Pinia，登录态走 localStorage，够用）；vite 端口 5174、`/api` 代理到 8082；tabbar 四宫格（首页/笔记/订单/我的）+ `keep-alive`；路由守卫 + token 持久化；axios 拦截器带 `token` 头、401 清态踢回登录。
  - [x] 1 登录页 + 首页：登录页调 `/api/auth/login` 落 token/uid/name；首页 = 秒杀横幅 + 热榜（`/api/nearby/hot`）+ 附近门店（`/api/nearby/stores`）+ 门店详情页（`/api/stores/{id}` + items/packages/reviews）。验证：5174 起、登录闭环跳转、两接口数据渲染。
  - [x] 2 券中心页（Seckill.vue）：可抢券列表 + 库存余量轮询刷新 + 抢券→轮询 `/api/seckill/orders/{id}`→成功弹窗；我的券在「我的」tab。验证：抢券返回正确幂等提示、券落库后「我的券」显示未使用。
  - [x] 3 下单页（OrderCreate.vue）：门店详情选项目/套餐带 query 跳转 → `POST /api/orders` → 自动模拟支付 → 跳「订单」。验证：HTTP 全链路（下单→支付→详情，status 1→2、actualAmount 268.0、订单号 CC202609162001）。
  - [x] 4 Feed 流页（Feed.vue）+ 发布（Publish.vue）：双游标分页、发布笔记、点赞。
  - [x] 5 我的页（Mine.vue）：我的车辆/我的券/退出登录。
  - [ ] 6（可后置）发布笔记→Feed 展示的真机回归；车辆档案录入页；订单按状态分组 tab（现为单列表）。
  - 环境备忘：app 也走 7890 代理的坑——Git Bash 里 curl 加 `--noproxy '*'`；Vant 全量引入 `vant/lib/index.css`；移动端 H5 调试开浏览器 device toolbar。
  - **踩坑记录（重要，面试/复盘可讲）**：① **Vant 4 已删除 `van-banner` 组件**（Vant 2 才有）——用了它不会报错，而是渲染成空自定义元素（`.van-banner` 节点数为 0、无任何内部 DOM），子内容无样式约束直接浮到 `van-nav-bar` 上，表现为"横幅文字压住导航栏标题"。教训：换大版本后逐个核对组件是否还在（`ls node_modules/vant/es`），构建通过不代表组件存在。改自定义 div 实现。② **`van-cell` 的 `is-link` 在 IAB 自动化里点不动**：坐标点击（CUA）、Playwright native click、`dom_cua` 节点点击三种方式 clickCount 均为 0（`elementFromPoint` 确认命中按钮但事件未派发），是自动化环境问题非页面 bug；改用带 query 的完整 URL 直接访问验证页面逻辑。③ OrderCreate 直接访问无 query 时会发空请求导致 "系统繁忙" 残留 toast → 加缺参防护（不调接口 + 明确 empty 提示）。
  - 简历条目（阶段 5-下完成后再写）：车主端 H5 全站功能闭环 + 移动端体验；可与「WebSocket 来单提醒」合并成一条「前后端全链路」条目。
- 2026-09-16：**阶段 5-下 车主端 H5 完成（car-care-app）**。Vue3 + Vite + Vant4，端口 5174、`/api` 代理 8082；4 个 Tab（首页/笔记/订单/我的）+ 5 个独立页（登录/门店详情/秒杀/下单/发布）。覆盖全部车主侧接口：登录鉴权、热榜与 GEO 附近门店、秒杀抢券异步轮询、下单+模拟支付、Feed 双游标分页、笔记发布点赞、我的券与车辆。
  - 验证数据：生产构建 372 模块 0 错误；登录闭环（user01 → 跳 /home、token 168 字符落盘、uid=6）；首页热榜+附近门店渲染（7.02/8.42km）；抢券幂等拦截「您已领取过该优惠券」且库存不减、券落库后「我的券」显示未使用；下单链路 下单→支付→详情（orderNo CC202609162001、status 1→2、actualAmount 268.0）；6 个页面布局几何无重叠
  - **踩坑修复**：`van-banner` 在 Vant 4 已被删除，用了不报错但渲染成空自定义元素，子内容浮到 nav 栏上（文字压住标题）→ 改自定义 div 实现；OrderCreate 无 query 直访时发空请求留残留 toast → 加缺参防护
  - 教训：跨大版本升级 UI 库后逐个核对组件是否存在（`ls node_modules/vant/es`）；`npm run build` 通过不等于组件可用
  - 环境坑：IAB 自动化对 `van-cell` 的 `is-link` 点击无效（坐标/native click/dom_cua 三种方式 clickCount 均为 0），改用完整 URL 带 query 访问做逻辑验证
  - 简历条目（草稿）：「实现车主端 H5 全站（Vue3+Vant）：登录鉴权、GEO 附近门店、秒杀抢券异步轮询、下单支付状态机、Feed 双游标分页、笔记发布点赞，打通前后端全链路」
- 2026-09-16：**阶段 6 打磨完成，项目全部收尾**。产出三份文档：
  - `README.md`：项目总览、Mermaid 架构图（客户端→后端→MySQL/Redis/RabbitMQ 数据流）、启动步骤、账号表、阶段成果、技术取舍对比表、FAQ
  - `document/performance-report.md`：全链路压测报告，指标由 `*.jtl` 原始样本直接复算（非复述本文件）；含方法论说明、缓存三场景、秒杀前后对比、GEO/Feed 正确性验收、诚实的边界声明
  - `document/SUMMARY.md`：演进路径理由、7 个核心设计取舍（含被否决的替代方案）、6 类踩坑复盘、6 条简历条目、8 个面试问答预案
  - 数据复核（从 jtl 重算）：关缓存 5000 请求 9.84s/508 QPS，开缓存 4.91s/**1018 QPS**（吞吐 2.0x，总耗时减半）；均值 RT 1.2ms→1.7ms 微升是走 Redis 的合理代价，已在报告中标注；击穿 200 并发仅 2 次回源；秒杀 seckill-final 20 成功/seckill-run 13 成功（差 7 正对应库存初始化 bug）
  - 补充统计（**截至阶段 6，不含阶段 7 的 OSS/支付/车辆档案**）：后端 91 类/4522 行，前端 2308 行，14 表，依赖 MyBatis-Plus 3.5.12 / Hutool 5.8.40 / JJWT 0.12.6 / springdoc 2.8.9
  - **文档交叉校验时又挖出一个同类 bug**：`Feed.vue` 用了 `van-avatar`，而 Vant 4.10.2 **同样没有这个组件**（与 van-banner 完全同型）。已改自定义 `.ava` 圆角 div 实现，构建通过
  - 组件核对判据修正：先前只 `ls node_modules/vant/es` 肉眼比对，漏看了 avatar 不在列表里（当时误报"banner 是唯一不存在的"，实为 19 种里有 2 种缺失）。单判据也会误报——`van-form`/`van-date-picker` 目录与导出都在但渲染类名不叫 `.van-form`，CSS 判据会误判缺失。最终用「目录存在 + `es/index.mjs` 导出」双判据
  - 说明与后续更正：本轮最初判断「所有中间件均停机」有误——
    - **MySQL 服务名是 `MySQL`**（监听 3306，一直在跑），不是 `MySQL80`。`MySQL80` 是残留未使用服务，已全文修正 README/PLAN
    - **配置里的 Redis 地址 `192.168.11.194` 已不可达**（无路由、ARP 无该主机）。用 `redis-cli` 定位到虚拟机当前实际地址为 `192.168.1.4:6379`（Redis 8.10.1 / Linux）。用 `-Dspring-boot.run.arguments=--spring.data.redis.host=192.168.1.4` 命令行覆盖启动，**未改配置文件**——是否要长期改 `application.yml` 由用户决定
    - `--spring.data.redis.host` 直接塞进 `jvmArguments` 会被 JVM 当未知参数拒绝（`Unrecognized option`），Spring Boot 应用参数须用 `spring-boot.run.arguments`
    - RabbitMQ 仍未启动（5672 未监听），但不阻塞后端启动（AMQP 连接惰性），3.5s 正常起来；依赖 MQ 的秒杀落库/延迟关单本轮未验证
    - **上述「不阻塞」需更正**：仅启动不受影响。实测一个实例在 RabbitMQ 全程未启动的情况下跑 3h17m（15:50→19:07），累积 **4452 次** `AmqpConnectException` / `Failed to check/redeclare auto-delete queue(s)` 后 exit code 1 退出。`SimpleMessageListenerContainer` 持续重试重声明 auto-delete 队列，长期失败会导致上下文失败 → 长时间开着后端必须先起 RabbitMQ。已写入 README FAQ 与 SUMMARY 踩坑表
    - 用户已于 19:02 直接改 `application.yml` 将 Redis host 从 `192.168.11.194` 改为 `192.168.1.4`（旧值注释保留），命令行覆盖方案不再需要；此后后端由用户在 IntelliJ IDEA 中运行
  - **van-avatar 修复已做真实渲染验证**：造最小测试数据（user01 关注 user02 + user02 发笔记 review#11，英文正文避开 Git Bash 中文转 GBK 坑），Feed 页实测 `.van-avatar` 节点数 0、`.ava` 渲染为 34×34px / border-radius 50% / 青绿渐变 / 白字"压"（昵称首字兜底），`.head` flex 居中 gap 8px 布局正确。证据图 `document/screenshots/feed_avatar_fixed.png`
  - 本轮产生的测试数据：t_follow(user01→user02)、t_review#11（user02 发于门店1），如需清理可直接删这两行
- 2026-09-17：**简历与面试准备完成**。新增两份专门文档，并把 SUMMARY.md 里重复的「简历条目/面试问答」两节改为指向，保证单一数据源：
  - `document/RESUME.md`：精简版(2行)/标准版(4行)/一句话版三种长度；**按 JD 关键词挑选表**（10 个方向各配一条独立条目）；STAR 口述模板；**数字速查表**（15 个可引用数字 + 各自出处，防止面试记错）；**「不要说的话」清单**（5 条夸大陷阱）；技术栈清单
  - `document/INTERVIEW.md`：15 个专题，每个按「面试官原话 → 直接回答 → 追问链 → 加分点」组织，覆盖缓存一致性/三件套/秒杀/ Lua 取舍/MQ 幂等与毒消息/死信关单/号段发号/Feed 推拉与双游标/GEO/WebSocket 集群/**压测方法论**/前端 Vant 坑/项目不足/基础速答/**OSS 上传与支付宝支付**；末尾附面试节奏建议
  - SUMMARY.md 现定位为纯「技术演进 + 取舍 + 踩坑」，踩坑表补入本轮 5 条环境与工具链坑（MySQL 服务名、Redis 地址过时、Maven 参数位置、`grep -c` 计数陷阱、Git Bash 下 Python 相对路径）
  - 简历材料严格只用实测数字（99.98%、2.0x、13→20、0 超卖），全部标注出处；未外推到生产容量
- 2026-09-17：**学习文档完成（document/LEARNING.md）**。定位与面试材料互补——面试材料是「讲给面试官听」，学习文档是「让自己真懂」：
  - 学习地图（6 层依赖关系，标明时间有限时的优先级）
  - 17 个核心概念速查（Redis 单线程与 Lua 原子、Cache Aside、穿透/击穿/雪崩区分、逻辑过期、幂等两形态、死信 TTL、双游标分页、推拉扩散、GEO 底层、ThreadLocal 泄漏、JWT 无状态、号段模式、WebSocket 集群路由、ServerEndpointExporter 陷阱、字符集、OSS 后端中转、支付宝回调验签）
  - 7 个技术点深潜：每个都是「原理 → **代码位置（带行数）** → **动手验证命令** → 常见误解」四段式
  - **10 个动手实验清单**：改什么参数、预期现象、学到什么。含 jtl 解析脚本与「只改一个变量」的方法论
  - 阅读代码推荐顺序（从 Result/BaseContext 开始，不从 main 开始）、四梯队延伸学习路径、12 项自检清单
  - 所有引用的代码路径与行数均实测核对（CacheHelper 80 行、VoucherSeckillService 183 行、SegmentService 157 行、RabbitConfig 93 行等）
- 2026-09-17：**阶段 7 上传+支付完成**。① 阿里云 OSS 图片上传：`POST /api/upload`（multipart，校验 image/* 与 ≤5MB，按 `car-care/yyyy/MM/uuid.ext` 分片存 OSS 返回公网 URL），复用 sky-take-out 同一套 AccessKey/bucket（`mycy-java`，endpoint `oss-cn-beijing`）；管理端门店封面/服务项目图（el-upload 回填字段+缩略图）、app 端发布笔记多图（Vant Uploader 最多 6 张，逗号拼接存 images）。② 支付宝沙箱支付：`POST /api/orders/{id}/pay` 由"模拟直接改状态"改为 wap 下单返回收银台 URL；`POST /api/notify/alipay`（放行登录拦截）`rsaCheckV1` 验签 + TRADE_SUCCESS 幂等置已支付（`OrderService.paySuccessByOrderNo`）；app 端「去支付」新窗口跳收银台、下单后不再自动支付（延迟关单逻辑不变）。③ 用户头像：LoginVO 返回 avatar、`GET/PUT /api/users/me` 个人中心（上传 OSS 后回写 avatar）、Mine 页点头像换图、Feed/门店评价展示作者头像（BlogVO.userAvatar 链路复用）。④ 移除管理台 Dashboard「版本路线」模块。
  - 依赖落地：`aliyun-sdk-oss 3.17.4`、`alipay-sdk-java 4.40.996.ALL`（首选的 4.39.26.ALL 在阿里云 Maven 镜像不存在，查 maven-metadata.xml 后换版）；编译与前端 build 全部通过
  - **待你提供**：支付宝沙箱 AppID/应用私钥/支付宝公钥填入 `application.yml` 的 `carcare.pay.alipay`；`notify-url` 需公网可达（本地用内网穿透映射 8082），否则沙箱回调进不来
  - 简历条目（草稿）：「图片上传走阿里云 OSS（后端中转、按日期分片、类型/大小校验），支付接入支付宝沙箱（wap 下单 + 异步回调 rsaCheckV1 验签 + 按订单号幂等驱动状态机）」
- 2026-09-17：**阶段 7 补充：车主注册 + 车辆档案管理**。① 注册：`POST /api/auth/register`（RegisterDTO 校验账号 4-20 位字母数字、密码 6-20 位非空白；账号唯一 + MD5 存储 + 固定 role=1 车主），app 端登录页改为「登录/注册」双页签（Vant tabs），注册成功回填账号自动切回登录。② 车辆档案：独立成 `VehicleController`（`GET /api/vehicles/my` + `POST/PUT/DELETE /api/vehicles/{id}`），归属一律按登录 token 取 userId 校验（`mustMine`），原挂在 CouponUserController 下的只读接口迁出并补齐增删改（CouponUserController 收敛为纯优惠券接口）；app 端 Mine 页「我的车辆」支持添加/编辑/删除（底部弹窗 + Vant Field 表单，日期用原生 date 输入）。编译与前端 build 全部通过。
  - 说明：注册接口位于 `/api/auth/**` 免登录路径内；车辆 `nextMaintainDate`（下次保养提醒）从此可由车主自行维护，此前只能 SQL 手工插种子数据
- 后续可选优化（非必需）：app 侧发布笔记→Feed 展示回归、订单按状态分组 tab；MySQL/RabbitMQ 注册为 Windows 服务开机自启；生产化补充多实例水平扩展与真实网络延迟验证；支付密钥就绪后的真机回调联调
