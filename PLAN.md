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
- 2026-09-24：**阶段 8 AI 养车顾问完成**。新增独立 Python 服务 `car-care-ai/`（FastAPI + LangChain 1.2 + LangGraph + Chroma），车主端 H5 加 AI 聊天页。规模：Java 新增 20 文件/1796 行，Python 24 文件/1817 行（另有 33 个测试/588 行），前端 3 文件/465 行，新增 2 张表（`t_ai_conversation`、`t_ai_message`），语料 5 篇/276 行切出 30 个向量分片。
  - **架构四决策**：① Java 是唯一网关，Python 只绑 `127.0.0.1` + `X-Internal-Token`，前端永远拿不到 AI 服务地址；② Python 完全无状态，会话历史由 Java 存 MySQL 并每次下发，因此不需要会话粘性、可随时重启；③ 数据双通道——用户私有数据由 Java 按 JWT 身份裁剪后下发（AI 侧没有任何「按 userId 查数据」的入口），公共目录数据走内部只读接口从而继承 Java 的过滤口径与 Redis 缓存；④ 超时预算分层，Python 单请求 120s < Java 空闲读 180s，让上游先认输而不是下游先断流。
  - **Java 侧**：只加 `spring-boot-starter-webflux` 用于拿 `WebClient`（Tomcat 仍是服务端，`@ConditionalOnWebApplication(SERVLET)` 照常生效，现有 4 个测试全通过）。`AiChatService` 用 `bodyToFlux(ServerSentEvent<String>)` 拿上游 SSE → 累积副作用 → `Flux<String>` 逐帧透传；客户端断连时 Reactor 自动取消订阅并中止上游请求，不用手写取消逻辑；落库放在 `doFinally` + 有界线程池（JDBC 阻塞不占事件循环）。
  - **Agent**：`create_agent` + 11 个工具（4 个查目录、5 个读下发上下文、1 个 RAG、1 个出草稿），`context_schema` 传请求级上下文使 Agent 无状态可并发复用；三个中间件——`dynamic_prompt` 注入当天日期、`wrap_model_call` 统计耗时/token、框架自带的 `ModelCallLimitMiddleware`/`ToolCallLimitMiddleware` 控预算。
  - **RAG**：Chroma 本地持久化 + bge-m3；语料按 `## ` 章节切分（而非按字数硬切）并给每个分片拼 `【文档·章节】` 前缀提升召回。**阈值 0.35 是实测标定的**：相关问题命中 0.43~0.68、无关问题 0.18~0.25，取两簇空档。标定时发现反例——「安心保返修政策」只拿到 0.428 分，照搬 travel 项目的 0.6 会把这条件正确答案滤掉。
  - **踩坑（都写成了回归测试）**：① 过渡语泄漏——模型调工具前说的「让我查一下」混进最终回答，根因是「本轮是否已调工具」的标志置位后不复位；② 修完①后更严重，最终回答全是过渡语，根因是工具调用分片按 **index** 累积而每轮 index 都从 0 重新开始，第二轮的调用撞上第一轮残留导致 `reset` 完全不触发 → 改为按工具调用 id 做键，并在 `ToolMessage` 时清空。教训：`stream_mode="messages"` 的 index 只在单轮内唯一。
  - **踩坑（Spring/Jackson）**：① 原以为 `Flux<String>` 是原样写出、自己拼了 `data: {...}\n\n`，实测被 Spring 二次包装成 `data:data:` → 改为只返回裸 JSON；② `WebClient.builder()` 用裸 Jackson 编码器，`LocalDateTime` 被序列化成 `[2026,10,20]` 数组导致 Python 422 → 既把容器 ObjectMapper 塞进 codec，也在契约上改为下发格式化字符串；③ SSE 响应没带 charset，严格客户端按 ISO-8859-1 解码中文全乱码 → `produces` 显式加 `charset=UTF-8`。
  - **模型行为坑**：① 模型不知道今天几号，把「这周六」算成 `2026-06-27`（当天是 09-24）→ 用 `dynamic_prompt` 注入当天日期；② 一次预约连调 6 个工具把模型调用预算耗尽，模型没机会写最终回答，用户看到一堆成功却被告知「抱歉没能生成」→ 轮数上限 3→5、提示词约束「一次最多 3 个工具、用户没问的别查」（实测 6→3 次）、兜底文案按「拿到了什么」分档（有草稿就说草稿已生成）。
  - **验证**：7 条越权路径实测全部拦截（跨用户评价/读会话/删会话/拿他人 sessionId 发消息/无 token/无内部令牌/令牌未配置时整体 500）；停掉 Python 后 `/api/ai/health` 返回 `available:false`、`/api/ai/chat` 返回友好 error 帧、`/api/orders/my` 等主链路完全正常；`mvn test` 4 个既有测试通过（**注意：`SegmentServiceTest.continuityAcrossSegments` 是偶发失败，见下方 2026-09-24 补记**）；`pytest` 33 个用例全通过；浏览器实测聊天页打字机、工具过程条、预约草稿卡片、历史回放（含工具轨迹与草稿还原）、点赞、悬浮球入口全链路可用。
  - **新增排障工具**：`document/tools/sse_probe.py` 直接观察 SSE 每一帧（登录 → 提问 → 逐事件打印），支持 `-s` 续会话与 `--raw` 看原始帧。
  - 简历条目（草稿）：「独立实现 AI 养车顾问模块（FastAPI + LangChain Agent + Chroma RAG），Java 侧以 WebClient/Reactor 做 SSE 逐帧透传、客户端断连自动中止上游生成；用户私有数据按 JWT 身份裁剪后下发，使 AI 服务无用户态、越权在结构上不成立；AI 服务不可用时主链路零影响」
- 2026-09-24：**阶段 8 补充：AI 助手 SSE 并发压测**（补齐阶段 8 收尾时唯一跳过的验收项）。新增 `document/jmeter/ai-chat-test.jmx`（目标可参数化：同一份脚本既能打 Java 网关，也能直连 Python 做对照）与 `document/tools/ai_direct_load.py`（绕过 Java 的直连对照脚本）。
  - **梯度并发（Java 网关，轻问题）**：1/5/20 并发几乎零退化（1540 → 1441 → 1520 ms）；50 并发 4791 ms、100 并发 5584 ms，**全程 0 错误 0 断流**。带工具的问题 20 并发 1882 ms、50 并发 4257 ms，同样 0 错误。
  - **瓶颈定位（关键方法）**：看**离散度**而不是均值——50 并发时 max/min 只有 1.20，连最快的那次也从 1.4s 涨到 4.3s。本地资源排队会呈长尾（先到快、后到排长队），**均匀拖慢才是上游吞吐受限**。再用 Python 中间件自测的模型调用耗时直接证伪：模型调用自身从 ~1.1s 涨到 ~2.6s（p90 4.5s），而这条链路上没有 Java。
  - **交叉对照隔离 Java 层开销**：同一份脚本、同样 `ramp=1`、同样问题，交替跑 A(Java 网关)/B(直连 Python) 共 4 对。**必须交替**——同配置前后两次跑自己就差了 671 ms，说明上游负载在分钟级漂移，不交替的话层间差异会直接混进漂移里。去掉首轮预热后 Java 均值 3124 ms、直连 2796 ms，**Java 层约 328 ms（≈12%）的加性成本**（JWT 校验 + 读历史 + 组装用户上下文 4~8 次查询 + 落库），与模型调用的 2.6s 不在一个数量级。
  - **正确性核对（比 RT 更重要）**：495 次请求 → 519−24=**495 条会话**、1043−53=**990 条消息**，一条不差；`message_count` 与实际消息数不符的会话 **0** 条。Java 日志压测窗口 27372 行 WARN **0**/ERROR **1**（那 1 条是调试时 MSYS 路径转换导致 404 留下的，与压测无关，留着当踩坑记录）；Python 2517 行 WARN/ERROR/Traceback 全 **0**；无 Hikari 连接池超时、无线程池拒绝。另核对了「有 user 消息无 assistant 回复」的孤儿会话共 3 条，时间戳全在压测前的 17:32~17:52（浏览器测「停止」按钮中断流留下，属预期），**压测窗口零孤儿**，证明 `doFinally` + 异步线程池的落库在 100 并发下不丢写。
  - **自调用死锁验证**：50 并发带工具的问题里，Python 会回调 Java 的 `/api/internal/ai/**` 取门店数据——这是最容易在并发下打死自己的模式（外层长连接占资源、内层还要再进来）。实测全部正常返回，0 错误。
  - **工具链坑（新发现）**：① **Git Bash 的 MSYS 路径转换会把 JMeter 参数改掉**——`-Jpath=/v1/chat` 被自动改写成 `D:/study_xue/Git/v1/chat`，请求 404 且**不报参数错误**，只看 JMeter 汇总会误判成服务问题，需加 `MSYS_NO_PATHCONV=1`；② 命令行传中文会被 Git Bash 与 JVM 二次编码，中文问题要用 JSON 原生 `\uXXXX` 转义形式传；③ 混编码日志里 `grep` 会按二进制处理而静默不匹配，改用 Python 读。
  - **诚实的边界**：100 并发仍 0 错误就停了，**第一个出错点在哪仍然未知**；上游是共享第三方 API，绝对 RT 随其负载漂移，本报告数字用于**定性归因**而非容量承诺；未测多实例 + 网关层。
  - 文档：`document/performance-report.md` 新增第六节（含复测命令与 6 条复测要点），`document/AI-DESIGN.md` 新增第八节（结论 + 两个方法论坑），`car-care-ai/README.md` 已知边界更新，`RESUME.md` 补 AI 条目/数字速查表/技术栈并修正过期规模数字（102 类 5171 行 → 123 类 6975 行，14 表 → 17 表）。
- 2026-09-24：**补记：`SegmentServiceTest.continuityAcrossSegments` 偶发失败（已修断言，根因未定位）**。收尾回归时 `mvn test` 挂了一次：`SegmentServiceTest.continuityAcrossSegments:88 expected: <1005> but was: <2005>`。
  - **确认与本轮 AI 改动无关**：本轮只新增 AI 模块的 Java 文件、只改了 `WebMvcConfig` 的放行路径与 `pom.xml`，没碰 `SegmentService` 及其测试；该用例只依赖 `SegmentService` + `t_seq_alloc`，与 AI 链路零交集。
  - **偶发，不是稳定失败**：修复前**至少实测到 1 次失败**；修复后连续跑 6 次全量 `mvn test`，**6/6 通过**（`Tests run: 4, Failures: 0`，零 `[ERROR]` 行，用 Python 解析日志逐份核对）；单独跑 `mvn test -Dtest=SegmentServiceTest` 3/3 通过。
    > 频率无法给准数：中途有一轮「跑 5 次」的统计是用 `grep` 读**重定向文件**判定的，而这个环境的 `grep` 是 shell 函数、对混编码日志按二进制处理会**静默返回空**，那 5 次的结论不可信（详见 `README.md` 常见问题最后一条）。**所以只能说「偶发」，不能引用具体概率。**
  - **现象**：`2005 = 1000 + 1005`，发号从 1001 开始，**第一个号段 (0,1000] 被整段跳过**。重复号数为 0（`set.size()==1005` 那条断言是过的），**没有正确性问题**。
  - **根因未定位（如实标注）**：第一版笔记我写成「预取与首次换段的时序竞争」，但那个说法**解释不了观测值**——按代码推演，首次 `allocate` 走 `base = max(newMax - step, notBelow)`，`notBelow = 占位段的 max = 0`、`newMax = 1000`，`base` 必然是 0，发号必然从 1 开始；且单次运行的日志里 DB `max_id` 涨到了 3000（三段），而 1005 个号按推演只需两段。**所以真正的触发条件没找到**，不要在面试里把它讲成已定论的时序问题。
  - **修复方式（按用户选择）**：把用例3 的断言从「精确断言 `min==1` / `max==1005`」改为「无重复 + 已发号区间内无空洞」（`max - min + 1 == set.size()`）。这个口径与组件契约一致——号段浪费是设计内允许的（`SegmentService.prefetch()` 注释「此时预取只会浪费一个号段」，实验4 断言「允许号段空洞」）。已用四种情形验证断言判定：正常 `[1,1005]` 通过、跳首段 `[1001,2005]` 通过、区间内真空洞失败、重复号失败——即修掉了误报，仍能抓住真问题。
  - 验证：`mvn test -Dtest=SegmentServiceTest` 3/3 通过；全量 `mvn test` 连跑 6 次 **6/6 通过**（逐份日志用 Python 核对，零 `[ERROR]`）。
- 2026-09-24：**修复：PyCharm 绿色按钮起不了 AI 服务**。用户反馈「用 PyCharm 的启动按钮启动不了」。排查后是**入口问题，不是环境问题**：`app/main.py` 里没有 `if __name__ == "__main__":`，点绿色按钮（本质是 `python app/main.py`）只是把模块 import 一遍就正常退出——控制台既不报错也没有日志，看着像"起不来"，其实进程已经跑完退出了。补了 uvicorn 入口，并顺手在文件开头加了 sys.path 兜底。
  - 为什么传 app 对象而不是 `"app.main:app"` 字符串：脚本方式运行时本模块的名字是 `__main__`，用导入字符串会让 uvicorn 再导入一份 `app.main`，进程里出现两个应用实例（被服务的是后者，`__main__` 那份的 lifespan 不跑，日志会自相矛盾）。代价是这种启动方式用不了 `--reload`。
  - 为什么需要 sys.path 兜底：`python app/main.py` 时 `sys.path[0]` 是 `app/` 而非项目根，顶部的 `from app import ...` 会 `ModuleNotFoundError`；`-m app.main` / uvicorn 启动时 `__package__` 有值，不走那段。
  - 环境侧已排除：PyCharm 2025.3 的解释器 `Python 3.13 (car-care-ai)` 确实指向 `car-care-ai/.venv/Scripts/python.exe`，`uvicorn 0.46.0` 已装在 `.venv` 里。另一个可能原因是时点问题——requirements.txt 解析失败时 pip 是**整体不装**的，那一刻连 uvicorn 都没有，现已随依赖修复一并解决。
  - 验证四种启动方式全部可用（各自 curl `/healthz` 返回 `ragReady:true, ragChunks:30`）：① `python app/main.py`（cwd=`app/`，即 PyCharm 对包内脚本的默认工作目录）② `python app/main.py`（cwd=项目根）③ `python -m app.main` ④ `python -m uvicorn app.main:app --host 127.0.0.1 --port 8000`（确认原命令没回归）；`pytest` 33/33 通过。README「3. 起服务」补了 IDE 启动方式与这两个坑。
- 2026-09-24：**新增 AI 模块学习文档 `document/AI-LEARNING.md`**（与 `document/LEARNING.md` 同体例：怎么用 → 学习地图 → 概念速查 → 逐点深潜（原理/代码位置/动手验证/常见误解）→ 推荐阅读顺序 → 动手实验清单 → 延伸路径 → 自检清单）。
  - **学习地图按依赖分 7 层**：第 0 层前置（SSE 帧格式 / pydantic 契约 / async 与阻塞 / 向量相似度）→ 第 1 层和大模型说话（messages 结构、流式与首字延迟、token 成本）→ 第 2 层 Function Calling（**模型不执行函数、只吐 JSON**；@tool 的 docstring 就是给模型看的接口文档；Agent 循环的三个停止条件）→ 第 3 层流式事件归一化（`stream_mode="messages"` 的二元组、`tool_call_chunks` 的 index 只在单轮内唯一、过渡语与 reset）→ 第 4 层 RAG（按 `##` 切分、前缀参与向量化、阈值标定、降级）→ 第 5 层服务化与跨语言（lifespan、无状态 Agent、SSE 透传、取消传导）→ 第 6 层工程判断（裁剪与越权、超时分层、降级开关、回归测试与压测）。
  - **概念速查 14 条**，逐点深潜 8 条（过渡语两个真 bug、工具返回值的双消费者与 ToolResult 两态、RAG 切分与前缀、阈值标定、无状态 Agent、SSE 透传与取消传导、上下文裁剪与「越权无法表达」、工具内校验），每条都带**文件行号**。
  - **动手实验 10 个**，全部基于现成环境可复现；其中实验 9 是「亲手把 bug 放回去看回归用例失败」，实验 3/4 是阈值标定。所有给出的命令都实测过：`requests 2.34.2` 已在 `.venv` 中（`sse_probe.py` 可直接跑）、`load_chunks()` 实测 30 片且前缀为 `【文档·章节】`。
  - **本轮新测的阈值证据**（不用起服务，直接看分数）：刹车片多久换 → 0.6217/0.6212/0.5869/0.4811；安心保返修政策 → 0.4651，其余骤降到 0.24 以下；今天天气怎么样 → 最高 0.2471。即相关 0.46~0.62、无关 0.19~0.25，**0.35 落在空档**，且「安心保」靠 0.4651 才过线（阈值设 0.5 以上会被滤掉）——与 `AI-DESIGN.md` 记录的标定结论一致，已把这段命令与实测输出写进 4.4。
  - README 文档树与文档索引已登记该文档。
- 2026-10-08：**简历定稿 v5 + 全仓术语/事实更正 + 首次把 AI 模块提交到远端**。
  - **简历 v5**（桌面 `初版简历_修订版_v5.docx` / `.pdf`，v4 保留）：车管家升为主项目并写入 AI 模块，旅游助手压为辅助项目。具体改动：① 项目顺序调换（车管家在前），车管家标题补「+ AI 服务」；② 车管家新增 2 条 bullet —— AI 养车顾问（独立 Python 服务 + Java 唯一网关 + 用户数据按 JWT 身份裁剪下发，越权在结构上不成立）与流式/并发验证（逐帧透传 SSE、断连中止上游生成、100 并发 0 错误 0 断流、495 次请求会话与消息零丢失、由模型耗时 1.1s→2.6s 定位瓶颈在上游）；③ 车管家技术栈补 Java/Python · FastAPI · LangChain · Chroma；④ 旅游助手由 5 条压到 3 条（压测结论并入 SSE 那条，删「会话记忆」与独立「性能验证」）；⑤ 专业技能补 Python、Chroma、LangChain Agent。
  - **排版硬约束复测**：仍是 **1 页**。方法是先把 v4 渲染成 PDF 量出真实行高（正文 9pt / 行高 11.9pt）与可用余量（下边距仅 280 twips=14pt，内容结束 y=781.6，可用下限 y≈828，**只有约 46pt≈3.9 行**），再按「AI 加 4 行 + 技术栈加 1 行 − 旅游减 3 行」配平。生成后实测内容最低点 y=791.6，**余 36.4pt**；并修掉了一处孤字行（AI 第一条原本第 3 行只剩「成立」两个字，裁掉「随请求」后收成 2 行）。
  - **生成方式**：用 python-docx 搬运 v4 的段落元素（deepcopy）只换文字、不动样式，脚本留在 `D:\study_xue\_resume_build\gen_resume_v5.py`（仓库外，未入库）。
  - **术语/事实更正（共 25 处，跨 7 个文件）**。两处说法与代码不符，之前复盘已判定必须改，这次清干净：
    - **「双缓冲」→「预取下一段 + 90% 异步触发」**。代码里是 `AtomicReference` 整体替换 + `Segment.next` 预挂，**没有两个交替的 buffer**，被问「双缓冲具体怎么实现」会答不上来。涉及 `RESUME.md`、`README.md`、`LEARNING.md`、`INTERVIEW.md`、`SUMMARY.md`、`phase2_seckill.sql` 注释、`SegmentService.java` 类注释与字段注释。
    - **「写事务回滚前回填旧值」→「删缓存后并发读回填旧值」**。`StoreService` 的写方法上**没有 `@Transactional`**，删缓存就发生在方法体里，不存在「回滚前」这个时点；真正的竞态是「读 miss → 回源 → 回填」这条链路横跨了写请求的「更新 DB + 删缓存」。涉及 `RESUME.md`、`README.md`，以及 `LEARNING.md`/`INTERVIEW.md`/`SUMMARY.md` 三处延迟双删的 T1–T4 时序图（已重画，并在 `LEARNING.md` 加了一段说明为什么不能照搬网上「事务回滚」的讲法）。
    - `PLAN.md` 的历史条目（2026-09-15 等）**保留原文不改**——它是按日期记录的日志，改掉等于伪造当时的认知；更正记在本条。
    - 验证：`git grep 双缓冲` 除 PLAN.md 外清零；`git grep 写事务回滚` 仅剩 `LEARNING.md` 里那句「不需要『写事务回滚』这个前提」；`mvn -o compile` 通过。
  - **入库前密钥扫描（本次的重点）**：写了个脚本先从真实配置（`car-care-ai/.env`、`application.yml`）里抽出 7 个敏感值——支付宝应用私钥(1624 字符)、支付宝公钥、SiliconFlow embedding key、JWT secret、内部调用令牌、DeepSeek key、OSS AccessKeyId——再逐个扫描 git 实际会提交的 255 个文件。**零命中**。同时确认 `car-care-ai/.env`、`car-care-ai/data/`、`.venv/`、`application.yml` 均未被暂存（`.env.example` 是占位符模板，应当入库）。扫描脚本在 `D:\study_xue\_resume_build\scan_secrets.py`。
    > 顺带记录一个环境坑：`C:\Users\Chen ye\AppData\Local\Temp\` 下堆了几百个解压出来的 `.py`，其中有一个 `typing.py`。任何以 Temp 为工作目录（或脚本放在 Temp）的 Python 进程都会被它遮蔽标准库 `typing`，报 `SyntaxError: source code string cannot contain null bytes`，且回溯指向毫不相关的 `docx/__init__.py`。脚本一律放干净目录。
  - 附带记录：`document/jmeter/seckill-users.csv` 里含一个本地测试用户的 JWT，是历史提交 `4b78eef` 就有的。该 token 由 `application.yml` 里的 JWT secret 签发，而 secret 从未入库，所以这个 token 只在本地同一套配置下有效，不构成泄露；留着不动。
- 2026-10-08：**修复 AI 预约「要前刹车却订到小保养」+ 补齐套餐草稿与下单页预填**。
  - **现象**：用户让 AI 约「前刹车」，结果草稿是「小保养」。
  - **复现方法**：起 Java(8082) + Python(8000) 后，直接打 Python 的 `/v1/chat/sync`（非流式，便于批量采样）与 `/v1/chat`（流式，能拿到 `tool_start`/`tool_end`），用真实用户上下文（张三 + 车 1 + 一笔小保养订单）跑同一句话多次。**不猜模型行为，只采样统计。**
  - **根因一（主因，已复现）：模型不调工具却宣称「草稿已生成」**。8 次采样里 **4 次**只调了 `search_stores`/`search_service_items` 就写出「帮你生成预约草稿……点卡片上的确认预约即可」，`build_booking_draft` 从未被调用，**卡片根本不存在**。根因是提示词硬约束 4 的原句「生成草稿后要说明：需要用户点击卡片上的确认按钮才会真正下单」——这句本意是约束语气，实际给了模型一句**可照抄的成品话术**，抄了就不必调工具。用户看不到卡片，却可能点上一轮遗留的旧卡片（旧卡片是之前聊小保养时生成的）→ 这就是「订到小保养」的路径。
  - **根因二（隐患，代码层已确认）：草稿归属校验拿分页结果比对**。`build_booking_draft` 内部用 `search_items(store_id=...)`（默认 `limit=10`、`orderByAsc(id)`）取回前 10 条再比对 `item_ids`（日志可见 `GET /api/internal/ai/items?storeId=1&limit=10`）。门店项目一旦超过 10 个，第 11 个之后的**合法项目会被判成「不属于该门店或在售状态已变更」**，而错误提示把「不存在」和「属于别家店」合并成一句，模型无法判断该换项目还是换门店，于是**从自己看得见的项目里换一个顶上**——小保养是每家店的第一个项目。现有种子数据门店 1 只有 8 个项目，所以这条**当前不触发**，但属于必然踩到的地雷，且是「要 A 订到 B」这类症状的典型机制。
    > 另外两条同类隐患一并修掉：① 取门店名用的是 `search_stores()`（无过滤、`limit=5`），门店多于 5 家时会静默退化成「门店 {id}」；② 套餐校验用 `list_packages(store_id)`（默认 `limit=5`）。
  - **根因三（排障时顺带发现）：`toolArgs` 恒为空**。`tool_start` 是在工具调用**第一个分片**就发出的，那一刻参数还没到齐；而 `key = tc.get("id") or f"idx{index}"` 让续接分片（**只有 `index`+`args`，没有 `id` 和 `name`**）掉进 `or` 兜底分支另起一个槽位，参数全被攒进另一个槽位。症状就是模型明明传了 `{"store_id":1,"item_ids":[6]}`，前端和日志里只有 `{}`/`null`——我这次想确认模型传了什么，正是被它挡住。
  - **修复**：
    1. **提示词层**：硬约束 4 改写为「**预约草稿只能由 `build_booking_draft` 产出，调用了才有卡片**；调用成功前不要说『草稿已生成』、不要描述草稿内容、不要提『卡片』和『确认预约』」。并补「这家店没有用户要的项目时如实说明并给出相近项目，**不要擅自替换**」。改写后同口径 8 次采样 **8/8 正确**（改前 4/8）。
    2. **代码兜底层**：`stream_chat` 收尾核对「回答在宣称草稿已生成」而「本次没有 draft 事件」→ 追加一个 `delta` 更正并记 warning（`_claims_draft`，末尾问句与否定词跳过，宁可漏判不误判）。正文已流式发出无法改写，只能补一句，且必须计入 `done.content` 否则落库文本与用户所见不一致。
    3. **内部接口加点查**：`/api/internal/ai/items` 新增 `ids`（**不分页、且不按 storeId 过滤**——带上 storeId 会让跨门店项目静默消失，又只剩「查不到」一种解释）；`/stores` 新增 `id`。`AiInternalService` 侧按 id 走 `selectList`，浏览路径仍走 `selectPage`。Python 客户端 `search_items(ids=[...])` / `search_stores(store_id=...)`。
    4. **草稿工具重写**：按 id 点查后**分别判断**「查不到」与「`storeId` 不等于目标门店」，两条提示分开写（前者让模型重查，后者明确要求「由用户决定换项目还是换门店，不要擅自替换」）；门店查不到（不存在/未营业）直接拒绝生成，不再退化成「门店 {id}」。
    5. **`toolArgs` 修正**：`tool_start` 不再发假的空 `{}`（改为不传），完整参数挂到 `tool_end`；新增本轮 `index → key` 映射 `round_keys` 供续接分片找回槽位，随分片缓存一起在轮末清掉。
  - **顺带补功能（用户要求「改对，不要偷懒」）**：
    - **套餐草稿**：`build_booking_draft` 新增 `package_id`（必须来自 `get_packages`，按 `store_id` 校验归属），`itemNames` 放套餐名（否则卡片「项目」那一行是空的），`packageName` 一并带上；**拒绝套餐与单项同时传**（平台一张订单只含一个套餐或一个项目，同时传会让前端只下其中一个、另一个静默消失）。前端卡片标签按 `packageId` 动态切「套餐/项目」，套餐单不再显示「一单只含一个项目」的提示。这条把原先完全走不到的死代码（`BookingDraft.packageId`、`goBooking` 的 `packageId` 分支、`OrderCreate` 的套餐分支）激活了。
    - **下单页时间选择器修正**：原先用 `van-date-picker` 只选日期，提交时把时间**硬编码成 `10:00:00`**——不管用户怎么点，到店时间永远是 10:00。改为 `van-picker-group`（日期 + 时间两步），确认时拼成 `yyyy-MM-dd HH:mm`，提交补 `:00`。这同时修掉了一个既有缺陷。
    - **预填车辆与到店时间**：`goBooking` 透传 `appointmentTime`/`vehicleId`；`OrderCreate` 在 `myVehiclesApi()` 返回后按 id 找回车辆（**找不到就留空让用户自己选**，AI 上下文是请求时刻快照，车可能已被删）。到店时间**先做格式校验再预填**：AI 给的是模型从用户话里抽的自由文本，原样塞进请求会被后端 `@JsonFormat("yyyy-MM-dd HH:mm:ss")` 打回 400，所以只认 `yyyy-MM-dd HH:mm`（允许尾随秒），且早于今天的日期一律不预填（选择器下限是今天，塞进去会选中非法值）。
  - **验证**：
    - Python `pytest` **43 passed**（原 33 + 新增 10：跨门店套餐被拒、套餐+单项互斥、门店不存在、区分「查不到/属于别家店」、`toolArgs` 落在 `tool_end`、多轮 index 复用、幻觉兜底正/反例）。新加的用例都按**真实分片形状**构造（续接分片不带 id），否则盖不住刚修的那个坑。
    - Java `mvn -o compile` 通过；`/items?ids=6,10` 实测跨门店返回两条（不再被 storeId 吞掉），`/stores?id=2` 点查正常，浏览路径 `/items?storeId=1&limit=10` 仍返回 8 条不变。
    - 前端 `npm run build` 通过。
    - **真实对话端到端**：8/8 出正确草稿（`itemIds=[6]` 前刹车片）；套餐 2/2（`packageId=1`、`itemNames=["安心小保养套餐"]`、¥668）；`tool_end` 已能看到 `{"store_id":1,"item_ids":[6]}`。
    - **浏览器实测**（本地 dev server + 张三账号）：聊天页发出「帮我约西湖文一店的前刹车片更换，明天下午3点，用我的浙A·88888」→ 卡片显示「前刹车片更换（一对）/ 2026-10-09 15:00 / ¥560.00」→ 点「确认预约」→ 跳转 URL 为 `/order-create?storeId=1&itemId=6&appointmentTime=2026-10-09+15:00&vehicleId=1`，下单页**车辆与到店时间均已预填**；再打开时间选择器改选 16 日 15:45，单元格正确回显 `2026-10-16 15:45`（证明 `picker-group` 的数组载荷解析无误）。
  - 文档：`AI-LEARNING.md` 3.6 的分片示意图**原本是错的**（把续接分片也画成带 `id`），已按真实形状改正并补上 `or f"idx{index}"` 这个坑；新增 4.9「不调工具却报成功」整节（提示词话术即幻觉模板 + 代码兜底 + 三条误解），4.8 补「校验必须点查、不能拿分页结果比对」。
  - 排障脚本留在 `D:\study_xue\_ai_debug\`（仓库外，未入库）。Java/Python 服务与前端 dev server 当前仍在运行（Java 8082、Python 8000、前端 5174）。**本次改动未提交 git**（10 个文件）。
- 后续可选优化（非必需）：app 侧发布笔记→Feed 展示回归、订单按状态分组 tab；MySQL/RabbitMQ 注册为 Windows 服务开机自启；生产化补充多实例水平扩展与真实网络延迟验证；支付密钥就绪后的真机回调联调；**AI 模块的限流与按用户/按天 token 成本核算**（现在只落库未汇总）、工具轮数用满时改「强制收尾轮」而不是靠兜底文案、**上探 AI 并发出错点**（100 并发仍 0 错误，上限未知）、知识库从工单/评价自动挖掘 FAQ、后台运营助手（复用同一套 Python 服务，换 admin 侧上下文与工具集）
