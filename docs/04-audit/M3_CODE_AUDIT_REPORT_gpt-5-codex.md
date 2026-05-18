# M3 代码审计报告 - gpt-5-codex

审计日期：2026-05-15  
审计范围：当前仓库主干工作区的源码、测试、配置、构建脚本、文档与运行验证。  
审计方式：静态代码阅读为主，结合 Maven 测试验证；未对业务代码做修改。

参考依据：

- 本项目源码与文档。
- Quarkus Vert.x 官方参考：https://quarkus.io/guides/vertx-reference
- Vert.x MQTT Server 官方文档：https://vertx.io/docs/vertx-mqtt/java/
- OASIS MQTT 5.0 规范：https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html

验证记录：

- `./mvnw -q test`：失败，原因是测试 HTTP 端口 `8081` 已被占用，触发 `QuarkusBindException`。
- `./mvnw -q test -Dquarkus.http.test-port=0`：通过。

## 一、项目整体理解

当前项目是一个基于 Quarkus + Vert.x 的单节点 MQTT Broker。整体结构是清晰的分层式设计：

- `bootstrap`：应用启动入口，负责生命周期与 Broker 启停。
- `config`：Quarkus 配置映射，覆盖 broker、transport、authn/authz、health 等配置。
- `transport.vertx`：基于 Vert.x MQTT Server 的网络接入层，负责接收 MQTT 包、转换内部请求模型、写回 ACK/PUBLISH。
- `protocol`：核心协议引擎，处理 CONNECT、SUBSCRIBE、UNSUBSCRIBE、PUBLISH、QoS1/QoS2 ACK 流、Will、会话恢复、诊断事件。
- `session`：内存会话与连接注册表，维护订阅、离线队列、QoS inflight、QoS2 状态。
- `routing`：订阅索引与主题匹配，目前是内存 Copy-on-Write 订阅树。
- `retained`：内存 retained message registry。
- `authn` / `authz`：认证授权链，支持静态配置与 no-match 策略。
- `observability`：日志事件、指标、健康检查、诊断事件。

核心运行流程是：

1. `VertxMqttBrokerTransport` 启动 MQTT server 并注册 endpoint handler。
2. CONNECT 到达后，transport 将 Vert.x endpoint/properties/will 转为内部 `ConnectRequest`，交给 `DefaultProtocolEngine.handleConnect`。
3. 协议层完成协议版本、属性、认证、授权、会话打开、Will 保存，再返回 CONNACK。
4. SUBSCRIBE/PUBLISH/ACK 类请求由 transport 转换为内部模型，协议层更新 session、routing、retained，并通过 `BrokerTransportSink` 回写消息。
5. 会话、订阅、retained、QoS 状态均在内存中维护，目前没有持久化。

M3 的实现已经覆盖了大量 MQTT broker 的关键骨架：QoS0/1/2、clean start/session expiry、retained、订阅选项、Topic Alias、部分 MQTT 5 属性、静态认证授权、健康检查、Micrometer 指标和结构化诊断日志。测试数量较多，说明当前代码不是纯 demo，而是已经进入可以持续演进的工程阶段。

## 二、总体评价

整体质量评价：中上。代码组织有明确边界，测试覆盖面比一般里程碑代码扎实，核心状态模型也基本成型。当前最大问题不是“跑不起来”，而是若干协议边界、生命周期一致性和失败路径语义还没有收紧。这些问题在简单 happy path 下不明显，但进入下一阶段的持久化、互操作、性能和生产化工作后会迅速放大。

当前 M3 是否适合进入下一阶段：不建议直接进入下一个里程碑的主体开发。可以开始下一里程碑的方案设计和技术预研，但在进入大规模实现前，应先修复本报告列出的高优先级问题，尤其是 MQTT topic `$` 规则、MQTT 5 重复属性校验、Will CONNECT 校验、session expiry 与 routing 生命周期一致性、transport 写失败后的 QoS 状态处理。

最大的 3 个风险：

1. **协议边界不严谨**：部分 MQTT 规范边界未实现或验证不足，例如 `$` 系统主题通配符规则、MQTT 5 singleton 属性重复、Will 属性与主题校验。
2. **状态索引可能不一致**：session expiry 可以清理 session，但 routing registry 中可能残留订阅绑定，未来持久化和离线消息会加重这类问题。
3. **失败路径语义不完整**：transport 写失败只记录诊断事件，没有明确反向驱动 QoS inflight、重试、断连或回滚状态，容易形成“状态看似存在但消息不再前进”的问题。

## 三、问题清单

| 编号 | 问题类型 | 严重程度 | 位置 | 问题描述 | 为什么这是问题 | 建议修复方案 |
|---|---|---|---|---|---|---|
| A-01 | MQTT 协议正确性 | 高 | `DefaultMqttTopicSupport.matches`；`ImmutableSubscriptionTreeNode.match`；`InMemoryRetainedMessageRegistry.findMatching` | 通配订阅 `#`、`+` 会匹配以 `$` 开头的系统主题。 | MQTT 规范要求订阅过滤器以通配符开始时不能匹配 `$` 开头的主题名。当前实现会让 `#` 收到 `$SYS/...`，也会让 retained replay 暴露系统主题。这是协议兼容与安全边界问题，进入互操作和系统主题支持前必须处理。 | 在 topic matching 入口统一增加规则：topic name 首字符为 `$` 时，filter 首字符也必须为 `$` 才允许匹配。订阅树匹配也要在第一层处理该规则，不能只修 `DefaultMqttTopicSupport`。补充 routing 与 retained 两组测试。 |
| A-02 | MQTT 5 属性校验 | 高 | `VertxMqttBrokerTransport.messageExpiry`、`responseTopic`、`correlationData`、`payloadFormatIndicator`、`contentType`、`sessionExpiryIntervalSeconds`、`receiveMaximum`、`maximumPacketSize`、`subscriptionIdentifier`；`DefaultProtocolEngine.hasInvalid*Properties` | 多个 MQTT 5 singleton 属性只读取第一个值，未拒绝重复属性。`subscriptionIdentifier` 明确读到了多个值但仍只取第一个。 | MQTT 5 中多个属性在同一个包内有唯一性或范围要求。当前代码会接受格式错误的客户端包，造成协议语义不准确，且文档里已经声明部分重复属性应触发 Protocol Error。M3 已把 MQTT 5 属性作为完成项，现在需要补齐，避免下一阶段基于错误行为继续扩展。 | 在 transport 层提取属性时保留重复计数或原始列表，在 protocol 层集中校验。对 CONNECT/SUBSCRIBE/PUBLISH/Will 属性分别建立 validator，重复 singleton 直接返回协议错误或断开。现有 `shouldExposeDuplicateSubscriptionIdentifierForProtocolValidation` 测试应改为真正断言协议拒绝。 |
| A-03 | CONNECT / Will 语义 | 高 | `DefaultProtocolEngine.handleConnect`、`authorizeWillPublish`、`publishWill`；`VertxMqttBrokerTransport.willMessage`、`willPublishProperties` | Will topic 和 Will properties 在 CONNECT 阶段未完整校验，只在异常断开真正发布 Will 时才间接经过 publish 路径。 | MQTT 的 Will 是 CONNECT 包语义的一部分，非法 Will 应在 CONNECT 时拒绝。当前实现可能接受非法 Will，存入 session，异常断开时再丢弃或产生诊断事件。这样客户端看到的是连接成功，但业务语义并不成立。Authz provider 还可能收到非法 topic。现在处理可以防止 Will、session 和 authz 模型继续背负错误假设。 | 在 `handleConnect` 内增加 `hasInvalidWill` 校验：topic name、payload/properties、message expiry、response topic、payload format、重复 singleton、QoS/retain 语义均在 CONNECT 阶段处理。拒绝后不打开 session、不注册 routing、不保存 Will。 |
| A-04 | 会话生命周期一致性 | 高 | `InMemorySessionRegistry.find`、`sessionCount`、`removeExpiredSessionIfAny`；`DefaultProtocolEngine.clearRoutingBindings` | session registry 懒清理过期 session 时，没有同步清理 routing registry 中的订阅绑定。 | routing 是 session 的派生索引。当前只有 connect 重连和 connection close 路径会清 routing。若一个持久会话过期后没有同 clientId 重新连接，订阅树可长期残留 stale clientId。发布时可能反复匹配到不存在的 session，形成性能和状态一致性债务。M4 如果做持久化，生命周期边界会更难修。 | 将 session expiry 做成显式 lifecycle 事件：registry 删除 session 时返回 removed session，protocol 或专门 coordinator 负责清 routing、清 retained 相关派生状态、发诊断事件。也可以在 broker 启动定时 sweep 过期 session 并统一清派生索引。 |
| A-05 | QoS 失败路径 | 高 | `VertxMqttBrokerTransport.sendPublishToSubscriber`；`DefaultProtocolEngine.routePublish`；`ClientSession` inflight 状态 | 发送给订阅者时，如果 endpoint 不存在、未连接、write 失败，transport 只记录诊断事件，没有通知 protocol 更新 QoS 状态。 | QoS1/QoS2 下 protocol 可能已经创建 inflight 记录。transport 写失败但连接未立刻关闭时，inflight 会占用 receive maximum，消息可能不再前进，也没有重试/重排/断连语义。M3 已实现 QoS2，失败路径现在需要收紧，否则后续持久化会持久化错误状态。 | 定义 outbound delivery result 回调：写失败应触发协议层处理。可选策略是立即关闭连接并让 session 恢复路径接管，或将消息重新放回离线队列并释放 inflight。策略必须按 QoS 区分并测试。 |
| A-06 | 安全默认配置 | 高 | `src/main/resources/application.yml`；`ConfiguredAuthnProvider.buildChain`；`ConfiguredAuthzProvider.buildChain` | 默认配置设置 `vxmq.broker.authn.no-match: allow` 和 `authz.no-match: allow`。当用户添加静态认证/授权规则但忘记覆盖 no-match 时，未匹配用户/操作仍可能放行。 | 这是“配置看起来启用了认证，实际 fail-open”的风险。M3 已交付静态认证授权能力，现在必须避免使用者误以为配置了一条用户密码后就完成了访问控制。 | 基线配置移除 no-match allow，或只在 dev/profile/demo 配置里显式允许。生产默认应 fail closed。文档明确区分“无认证开发模式”和“配置认证规则后默认 deny”。补充集成测试覆盖静态用户未匹配时拒绝。 |
| A-07 | 架构复杂度 | 中 | `DefaultProtocolEngine` | `DefaultProtocolEngine` 约 1400 行，集中了连接、订阅、发布、QoS、Will、诊断、属性校验、大小限制、session lifecycle 等职责。 | 当前还能维护，但 M4 若加入持久化、恢复、事务/幂等、更多 MQTT 5 规则，这个类会成为主要变更热点。A-02/A-03/A-05 都说明职责已经过密。现在应先拆边界，避免下一阶段在单类上继续堆逻辑。 | 按职责拆出 `ConnectProcessor`、`SubscribeProcessor`、`PublishProcessor`、`QosFlowCoordinator`、`WillService`、`Mqtt5PropertyValidator` 或等价内部组件。先从 validator 和 outbound failure coordinator 拆起，风险最低。 |
| A-08 | Transport 适配层过重 | 中 | `VertxMqttBrokerTransport` | transport 同时负责 endpoint 生命周期、包转换、属性读取、ACK 回写、出站 publish、diagnostic、close 处理，约 800 行。 | transport 层过重导致协议规则分散。比如 MQTT 5 属性重复问题一半在 transport，一半在 protocol，最终没有完整校验。未来 TLS、WebSocket、外部互操作、mock transport 测试都会受影响。 | 抽出 `VertxMqttPacketMapper`、`VertxMqttPropertyReader`、`VertxOutboundWriter`。transport 保留生命周期和 handler wiring。属性读取应能表达“缺失/单值/重复/非法类型”。 |
| A-09 | 断连诊断顺序风险 | 中 | `VertxMqttBrokerTransport.closeHandler`；`ClientConnectionRegistry.close`；`DefaultProtocolEngine.handleConnectionClosed` | close handler 先把 connection registry 状态置为 `CLOSED`，再调用 protocol 的 close 处理。protocol 通过 connection state 判断是否客户端主动 DISCONNECT。 | 状态判断依赖调用顺序，容易在不同 close 路径下产生不准确诊断原因。诊断与指标是 M3 重点能力，现在应避免被错误状态污染。 | 在 transport 捕获 close 前的状态或 close reason，并作为显式参数传给 protocol。不要让 protocol 从已被修改后的 registry state 反推原因。 |
| A-10 | 可观测性计数偏差 | 中 | `DefaultProtocolEngine.handleConnect`；`VertxMqttBrokerTransport.handleEndpoint`；`LoggingBrokerEventSink.diagnostic` | CONNECT 被拒绝时，protocol 和 transport 都会记录 warn 级诊断事件，可能导致同一次拒绝被重复计入 protocol warning。 | M3 指标目标是定位问题；重复计数会让仪表盘误判错误率。现在处理成本低，后续再清理历史指标语义会更麻烦。 | 明确诊断事件层级：protocol 负责业务拒绝，transport 只记录写回失败或网络异常。或给事件增加 correlation/phase，并让 metrics 只统计一类。 |
| A-11 | 热路径日志过重 | 中 | `LoggingBrokerEventSink.subscriptionAdded`、`subscriptionRemoved`、`messageRouted` | 每次订阅变更和每条消息路由都会 INFO 记录 clientId/topic/topicFilter。 | 在负载稍高时会造成日志放大，也可能泄漏业务 topic。当前已有 Micrometer 计数，INFO 热路径日志不是最佳默认。M3 进入性能/运维前应收敛日志等级。 | 默认改为 DEBUG/TRACE，INFO 只保留生命周期和异常摘要。topic/clientId 细节进入诊断事件或采样日志。 |
| A-12 | 测试可重复性 | 中 | `src/test/resources/application.yml`；Maven/Quarkus test 配置 | 默认 `./mvnw -q test` 会绑定固定测试 HTTP 端口 `8081`，本次审计中因端口占用失败。 | 回归测试应该是最可靠的质量门。固定端口会让本地、CI 并行任务和 IDE 测试不稳定。现在修复可以避免后续每次审计/CI 都被环境噪音干扰。 | 在 test profile 中设置 `quarkus.http.test-port=0`，或在 surefire 配置中传入随机端口。保留需要固定端口的单测时应单独说明。 |
| A-13 | 文档与实现不一致 | 低 | `docs/02-system/state-and-routing.md`；`docs/02-system/message-delivery.md` | 文档仍写着暂不支持 QoS2、Subscription Options、Subscription Identifier；Will QoS2 延后。但源码和测试已经支持相关能力。 | 文档是下一里程碑设计输入。不一致会导致错误决策和重复实现，尤其是状态/路由/消息投递文档直接影响 M4。 | 更新系统文档，将 M3 已完成能力、仍缺能力和明确限制分开。将“当前不支持”改为“当前支持但仅内存/无重试/无持久化”等更准确描述。 |
| A-14 | 依赖面偏宽 | 低 | `pom.xml` | `quarkus-rest`、`quarkus-rest-jackson`、`quarkus-messaging`、`quarkus-messaging-kafka` 当前未见实际业务使用。 | 未使用依赖会扩大 native image、启动、依赖冲突和安全扫描面。当前不是功能 blocker，但进入性能和生产化前应清理或给出理由。 | 删除未使用依赖，或在 ADR/README 中说明保留原因。如果下一里程碑会用 Kafka，应延迟到实际实现时再加入。 |

## 四、重点风险深挖

### 1. `$` 系统主题与通配匹配不符合 MQTT 规范

现状：`DefaultMqttTopicSupport.matches` 对 `#`、`+` 做了通用匹配；`ImmutableSubscriptionTreeNode.match` 会无条件把当前节点的 `#` binding 加入结果；retained replay 也通过相同 topic support 进行匹配。代码没有特殊处理 `$` 开头的 topic name。

风险：订阅 `#` 或 `+/#` 的普通客户端可能收到 `$SYS/...` 或未来的 broker 系统主题。即使当前还没有完整 `$SYS` 功能，这也是协议边界和未来安全边界问题。

可能触发场景：

- 客户端订阅 `#`，broker 未来发布 `$SYS/broker/clients`。
- 管理插件或内部功能使用 `$` topic 承载状态。
- retained registry 保存了 `$` topic，普通 wildcard 订阅连接后收到 retained replay。

推荐修改方案：在所有 topic matching 入口统一实现规则：当 topic name 以 `$` 开头时，subscription filter 必须也以 `$` 开头才可能匹配。订阅树要在第一层判断，不能只修字符串 matcher，否则 routing path 仍会错。建议补充 `DefaultMqttTopicSupportTest`、`InMemorySubscriptionRegistryTest`、`InMemoryRetainedMessageRegistryTest` 三层测试。

是否建议立即修复：是。修复范围小，收益大，且会影响互操作测试结论。

### 2. MQTT 5 属性重复与范围校验缺口

现状：transport 多处使用 `properties.getProperty(...)` 取单个属性值。对于 subscription identifier，代码已经调用 `getProperties(...)`，但仍返回第一个值。protocol 只做了少量范围校验，例如 receive maximum 不能为 0、maximum packet size 不能为 0、subscription identifier 不能小于 1、response topic 不能含 wildcard。

风险：broker 会接受一些 MQTT 5 非法包。更严重的是，内部模型只保存第一个属性值，一旦进入 protocol 层，已经丢失“重复属性”的事实，后续很难正确拒绝。

可能触发场景：

- PUBLISH 携带两个 Response Topic 或 Correlation Data。
- SUBSCRIBE 携带多个 Subscription Identifier。
- CONNECT 携带多个 Receive Maximum、Maximum Packet Size、Session Expiry Interval。
- Will properties 携带重复 singleton 属性。

推荐修改方案：建立 `Mqtt5PropertyValidation` 之类的集中校验组件。transport 层不应把重复属性压扁为单值，而应向 protocol 暴露原始属性计数或一个 validation result。CONNECT/SUBSCRIBE/PUBLISH/Will 分别校验后再构造内部 request，或构造 request 时携带 validation errors。

是否建议立即修复：是。M3 已宣称 MQTT 5 属性支持，如果现在不修，后续测试会固化错误语义。

### 3. Will 在 CONNECT 阶段没有形成完整闭环

现状：Will message 由 transport 从 endpoint 读取并传入 `ConnectRequest`。protocol 在 `handleConnect` 中只做 Will publish 授权，然后打开 session 并保存 Will。Will topic/properties 的合法性没有在 CONNECT 阶段完整校验。真正异常断开时，`publishWill` 再通过 `routeServerPublish` 走发布路径。

风险：非法 Will 可导致“连接成功但 Will 永远不会按协议发布”的状态。客户端认为 CONNECT 成功，broker 内部却保存了一个不应接受的 Will。异常断开时再处理已经太晚。

可能触发场景：

- CONNECT 携带包含 wildcard 的 Will topic。
- Will properties 中 Response Topic 含 wildcard。
- Will properties 中重复 singleton 属性。
- authz provider 对非法 topic 做了未定义处理。

推荐修改方案：把 Will 当作 CONNECT 包的一部分做完整校验。校验失败时直接返回 CONNACK 错误或断开，且不打开 session、不注册 connection、不保存 Will。建议 `ConnectRequest` 增加 Will validation result，避免校验逻辑分散在 transport 和 protocol。

是否建议立即修复：是。Will 是连接语义，不应延后到断线时修正。

### 4. Session expiry 与 routing 派生索引不一致

现状：`InMemorySessionRegistry.find` 和 `sessionCount` 会懒清理过期 session，但这个清理动作只发生在 registry 内部，没有回调 protocol 清理 routing。`DefaultProtocolEngine.clearRoutingBindings` 只在 connect/session takeover 和 connection close 相关路径被调用。

风险：session 已过期但 routing tree 仍保留其订阅。发布时可能匹配到 stale clientId，然后因找不到 session 而跳过。这一类问题短期可能只是浪费匹配成本，长期会在持久化、离线队列、metrics 中制造更隐蔽的不一致。

可能触发场景：

- 客户端创建持久会话并订阅，断开后直到 expiry 到期也不再上线。
- 期间没有相同 clientId 重新连接触发 clear。
- broker 持续运行并处理大量 topic publish。

推荐修改方案：把 session 删除变成显式生命周期事件，而不是 registry 私下完成。可以新增 `SessionExpiryService` 定时扫描，也可以让 `find` 返回“已删除 session”事件给 protocol coordinator。只要 session 被删除，就同步清 routing、offline queue、inflight、诊断事件和相关 metrics。

是否建议立即修复：建议在 M4 开始前修复。它不是立即导致功能不可用，但会直接影响持久化设计边界。

### 5. 出站写失败没有反馈给协议状态机

现状：`VertxMqttBrokerTransport.sendPublishToSubscriber` 对 endpoint 不存在、未连接、write 失败只写 diagnostic。协议层不知道消息没有真正送到 socket。

风险：QoS1/QoS2 消息可能已经进入 inflight。如果写失败但 close handler 没有及时执行，inflight 状态会长期占用，receive maximum 被耗尽，后续消息无法推进。即使最终断线，也缺少“写失败导致断线/重排”的明确语义。

可能触发场景：

- TCP socket 在写出时失败。
- endpoint 状态和 registry 状态短暂不一致。
- 客户端 receive maximum 很小，单条失败 inflight 足以阻塞后续消息。

推荐修改方案：transport 写操作返回结果或调用 callback 给 protocol。QoS0 可只记录并丢弃；QoS1/QoS2 应释放/重排/关闭连接三选一，并以测试固定语义。短期建议采用最保守策略：写失败即关闭连接，让 session 恢复和离线队列处理后续重投。

是否建议立即修复：是，至少要确定策略并补测试，否则 QoS2 的可靠性声明不够稳。

## 五、重构建议

### 必须立即改

- 修复 `$` 系统主题通配匹配规则，覆盖 routing 与 retained replay。
- 增加 MQTT 5 singleton 属性重复校验，至少覆盖 CONNECT、SUBSCRIBE、PUBLISH、Will properties。
- 在 CONNECT 阶段完整校验 Will topic 和 Will properties。
- 明确出站写失败后的协议状态处理，避免 QoS inflight 卡死。
- 将测试端口改为随机端口，确保 `./mvnw test` 默认可重复执行。

### 下个里程碑前建议改

- 将 session expiry 清理从 registry 私有副作用改成显式 lifecycle，由 protocol 统一清理 routing 派生索引。
- 从 `DefaultProtocolEngine` 拆出 MQTT 5 property validator、Will service、QoS flow coordinator。
- 从 `VertxMqttBrokerTransport` 拆出 packet/property mapper 与 outbound writer。
- 收敛热路径 INFO 日志，保留指标与可采样诊断。
- 更新 `docs/02-system` 中与 M3 实现不一致的状态、路由、消息投递文档。

### 可以暂缓

- 完整持久化、跨节点一致性、共享订阅、外部 broker 级互操作矩阵。
- QoS1/QoS2 定时重发策略；当前可以先通过断线恢复避免卡死，但需要在 M4/M5 明确。
- routing tree 的性能优化。当前 Copy-on-Write 方案可继续支撑单节点内存实现，但需要在性能基线中验证。
- 依赖清理可以与 native image/perf baseline 一起做。

### 不建议改

- 不建议现在替换 Quarkus + Vert.x 技术栈。当前技术栈与目标匹配，问题主要在协议边界和状态生命周期，不在框架选择。
- 不建议现在引入复杂分布式一致性框架。当前还是单节点内存 broker，过早引入会增加实现噪音。
- 不建议为了“整洁”大规模重写 `DefaultProtocolEngine`。应先拆验证、Will、outbound failure 这些高风险边界，保持行为可回归。

## 六、测试补强建议

| 测试名称 | 测试目标 | 覆盖场景 | 优先级 |
|---|---|---|---|
| `shouldNotMatchDollarTopicWithHashWildcard` | 验证 `$` 系统主题不被普通 `#` 匹配 | `#` vs `$SYS/broker/clients`；`$SYS/#` 可匹配 | 高 |
| `shouldNotReplayDollarRetainedMessageToPlainWildcard` | 验证 retained replay 遵循 `$` 规则 | retained `$SYS/x`；订阅 `#` 不收到；订阅 `$SYS/#` 收到 | 高 |
| `shouldRejectPublishWithDuplicateSingletonProperties` | 验证 PUBLISH MQTT 5 重复 singleton 属性拒绝 | Response Topic、Correlation Data、Payload Format Indicator、Content Type、Message Expiry | 高 |
| `shouldRejectSubscribeWithDuplicateSubscriptionIdentifier` | 验证 SUBSCRIBE 重复 Subscription Identifier 拒绝 | 同一个 SUBSCRIBE 包含两个 subscription identifier | 高 |
| `shouldRejectConnectWithDuplicateSingletonProperties` | 验证 CONNECT 重复 singleton 属性拒绝 | Receive Maximum、Maximum Packet Size、Session Expiry Interval | 高 |
| `shouldRejectConnectWithInvalidWillTopic` | 验证非法 Will 在 CONNECT 阶段拒绝 | Will topic 含 wildcard、空 topic、非法 UTF-8/层级规则 | 高 |
| `shouldRejectConnectWithInvalidWillProperties` | 验证 Will properties 在 CONNECT 阶段校验 | Will response topic 含 wildcard、重复 singleton、非法 payload format | 高 |
| `shouldClearRoutingBindingsWhenSessionExpires` | 验证 session expiry 同步清 routing | 持久会话订阅后断开，到期后 publish 不再匹配 stale clientId | 高 |
| `shouldReleaseOrRequeueInflightWhenOutboundWriteFails` | 验证出站写失败不会卡死 QoS inflight | QoS1/QoS2 写失败、receive maximum=1、后续消息仍可推进或连接被关闭 | 高 |
| `shouldDenyUnmatchedUserWhenStaticAuthConfigured` | 验证认证配置 fail closed | 配置一个静态用户，未知用户连接应拒绝 | 高 |
| `shouldNotDoubleCountRejectedConnectWarnings` | 验证 rejected CONNECT 指标不重复 | 一次认证失败只产生一个主要 warning 计数 | 中 |
| `mavenTestShouldUseRandomHttpPort` | 验证默认测试命令可重复执行 | 不依赖固定 8081；并行/占用场景不失败 | 中 |
| `shouldPreserveDisconnectReasonInDiagnostics` | 验证断连诊断原因准确 | 正常 DISCONNECT、socket close、protocol error 三种路径 | 中 |
| `interopShouldPassBasicMosquittoOrPahoScenarios` | 增加外部客户端互操作信心 | CONNECT/SUB/PUB/QoS1/QoS2/retained/session expiry 基本矩阵 | 中 |

## 七、下一步行动计划

1. **先修测试稳定性**：把测试 HTTP 端口改为随机端口，确保 `./mvnw test` 默认可作为可靠回归门。
2. **修协议边界高风险问题**：一次性处理 `$` topic wildcard、MQTT 5 singleton 重复属性、CONNECT Will 校验，并补对应单元/集成测试。
3. **修 QoS 出站失败语义**：定义 transport 写失败后的协议回调策略，至少保证 QoS1/QoS2 不会永久占用 inflight。
4. **修 session lifecycle 一致性**：让 session expiry 触发 routing 清理，消除派生索引和 session truth 的分裂。
5. **收敛安全默认配置**：将 authn/authz no-match 的默认行为改为 fail closed，开发模式显式开启 allow。
6. **拆小高风险组件**：先抽 `Mqtt5PropertyValidator`、`WillService`、`OutboundDeliveryCoordinator`，不要做无目标大重构。
7. **更新系统文档**：同步 M3 已支持/未支持/限制项，尤其是状态、路由和消息投递文档。
8. **建立下一阶段质量门**：在 M4 前固定协议边界测试、session lifecycle 测试、QoS failure 测试和一组外部客户端互操作测试。

## 最终判断

当前代码可以认为完成了 M3 的主要功能骨架，但不建议在未整改高优先级问题前直接进入下一个里程碑的主体开发。更准确的判断是：**可以进入下一里程碑的设计准备阶段；进入实现阶段前，应先完成本报告 A-01 到 A-06 的修复与回归测试。**

