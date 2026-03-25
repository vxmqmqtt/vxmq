# 模块设计

本文档描述当前代码基础上的模块职责和边界约束。它是长期设计文档，不承担阶段任务拆解与状态汇报职责。

## 设计原则

- 状态归属清晰：连接级、会话级、路由级状态各自有唯一所有者。
- 分层单向：网络事件进入 `transport`，协议决策集中在 `protocol`，状态更新落在 `session` 与 `routing`。
- 宿主与核心分离：Quarkus 管生命周期和依赖注入，Broker 核心逻辑尽量不向下扩散框架细节。
- 传输层可使用 Mutiny / Vert.x 类型，核心领域层优先使用纯 Java 模型。
- 主链路保持非阻塞，避免在 event loop 上引入阻塞式 IO。

## 当前模块

### `bootstrap`

职责：

- 监听 Quarkus 启停事件
- 启动和停止 Broker transport

### `config`

职责：

- 提供 broker 监听地址、端口、消息大小限制和连接超时等运行配置

### `transport`

职责：

- 持有 `MqttServer` 与在线 `MqttEndpoint`
- 接收 MQTT 报文回调并转换为内部请求对象
- 将协议处理结果映射为 CONNACK、SUBACK、UNSUBACK、PUBLISH 或断连动作

边界：

- 不保存会话真相
- 不定义 Topic 匹配规则
- 不直接修改订阅状态，必须通过 `protocol`

### `protocol`

职责：

- 聚合 CONNECT、SUBSCRIBE、UNSUBSCRIBE、PUBLISH、DISCONNECT 的处理决策
- 组合 `auth`、`session`、`routing` 和 `connectionRegistry`
- 输出标准化结果模型，供 `transport` 映射为具体报文行为

### `session`

职责：

- 管理按 `clientId` 归属的会话视图
- 当前已落地内容包括：绑定连接 ID、订阅集合
- 为后续扩展 Session Expiry、inflight message、offline message 预留位置

### `routing`

职责：

- 管理订阅索引
- 提供 Topic Filter 匹配与订阅查询
- 支撑发布消息的命中集合解析

### `auth`

职责：

- 提供连接鉴权扩展点
- 当前默认实现为 `permit-all`

### `observability`

职责：

- 记录连接接受、协议告警、订阅变更、消息路由等关键事件
- 为后续日志、指标和诊断输出提供统一出口

## 关键状态归属

### 连接级状态

由 `ClientConnection` 和 `ClientConnectionRegistry` 持有：

- 内部连接 ID
- 当前协议版本
- `clientId`
- clean session 标志
- 生命周期状态
- 当前活跃连接索引

### 会话级状态

由 `SessionRegistry` 持有：

- `clientId`
- 当前绑定连接 ID
- 当前订阅集合

### 路由级状态

由 `SubscriptionRegistry` 持有：

- Topic Filter 到订阅绑定的索引
- Topic Name 命中结果

## 调用方向

允许的主要调用方向：

- `bootstrap -> transport`
- `transport -> protocol`
- `protocol -> auth`
- `protocol -> session`
- `protocol -> routing`
- `protocol -> connectionRegistry`
- `protocol -> observability`

不允许的主要方向：

- `routing -> transport`
- `session -> transport`
- `transport` 绕过 `protocol` 直接修改会话或订阅状态

## 演进约束

- 若未来引入持久化，不应破坏 `protocol -> session / routing` 的职责边界。
- 若未来引入 QoS 1 / QoS 2，不应把 inflight 状态机直接塞回 `transport`。
- 若未来引入更多 MQTT 5 属性映射，可在 `transport` 内部抽出更明确的报文适配层，但当前无需虚构独立 `codec` 模块。
