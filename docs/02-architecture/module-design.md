# 模块设计

本文档聚焦 `M1` 最小闭环阶段的模块拆分，目标是在编码前明确：

1. 模块职责边界。
2. 关键状态归属。
3. 主要调用方向。
4. 后续向 `M2+` 演进时的扩展点。

## 设计原则

- 连接层、协议层、状态层、路由层分离，避免单个类同时承担网络、协议和业务状态职责。
- 连接级状态与会话级状态分离，避免 `M1` 阶段就把短生命周期对象和长生命周期对象耦合死。
- 先以内存实现最小闭环，但接口命名与职责要为 `M2/M4` 的持久化和恢复留口。
- 所有出站报文都应经过统一的编码与发送出口，便于审计和后续限流。
- 主链路默认运行在 Vert.x event loop 上，因此 `M1` 热路径必须保持非阻塞。

## 已确定约束

- `transport` 入口使用 Vert.x MQTT server，见 `ADR-0004`。
- `M1` 主链路采用响应式、非阻塞、event-loop 模型，见 `ADR-0005`。
- 在 Quarkus 中接入 Vert.x 扩展时，优先使用 Mutiny 包装层；仅在没有 Mutiny 变体或需要底层配置对象时才使用原生 Vert.x 类型。

## M1 目标范围

`M1` 需要覆盖：

- CONNECT / CONNACK。
- PUBLISH 主链路。
- SUBSCRIBE / SUBACK。
- UNSUBSCRIBE / UNSUBACK。
- QoS 0。
- Topic Filter / Wildcard。
- Keep Alive 与基础断连处理。

`M1` 明确不做：

- QoS 1 / QoS 2 状态机。
- 会话持久化恢复。
- Retained Message。
- Will Message。
- Shared Subscription。

## 建议模块

### 1. transport

职责：

- 管理 TCP 连接生命周期。
- 接收字节流并交给 MQTT 解码器。
- 将编码后的报文写回网络连接。
- 感知连接关闭、读空闲、写异常等连接级事件。
- 封装 Vert.x MQTT connection，向上暴露项目自己的连接抽象。

输入：

- 网络事件。
- 编码后的响应报文。

输出：

- 解码后的 MQTT 报文。
- 连接建立 / 关闭 / 空闲事件。

边界：

- 不负责协议合法性决策。
- 不负责主题路由和订阅状态。
- 不把 Vert.x 具体类型泄漏到 `protocol/session/routing` 核心接口。

### 2. codec

职责：

- MQTT 报文编解码。
- 基础报文结构校验，例如固定报头、剩余长度、字段存在性。

输入：

- 字节流。
- 协议对象。

输出：

- 强类型 MQTT 报文对象。
- 编码后的二进制数据。

边界：

- 不做业务语义校验，例如是否允许当前客户端发送某类报文。

### 3. connection

职责：

- 表示当前在线连接及其连接级状态。
- 保存连接关联的 `clientId`、协议版本、Keep Alive 配置、连接状态。
- 负责连接与 `session` 的关联。

建议状态：

- `NEW`
- `CONNECTING`
- `CONNECTED`
- `DISCONNECTING`
- `CLOSED`

边界：

- 连接对象只保存在线期间必需状态，不承担长期会话存储职责。

### 4. protocol

职责：

- 作为 MQTT 报文入口调度层。
- 根据报文类型分发到对应处理器。
- 做协议语义校验和错误码决策。

建议拆分：

- `ConnectHandler`
- `PublishHandler`
- `SubscribeHandler`
- `UnsubscribeHandler`
- `DisconnectHandler`

边界：

- 不直接持有 Topic Tree 或 Session Map 的底层实现细节。
- 通过接口调用 `session`、`routing`、`auth`、`observability`。
- 热路径处理必须短小、无阻塞、event-loop 友好。

### 5. session

职责：

- 管理客户端会话抽象。
- 在 `M1` 中至少承载客户端订阅集与在线关联关系。
- 为 `M2` 的 Session Expiry、未完成消息、离线消息预留扩展位。

建议对象：

- `Session`
- `SessionRegistry`
- `SessionBinding`

`M1` 最小字段建议：

- `clientId`
- `subscriptions`
- `connected`
- `connectionId`

边界：

- `M1` 中可以以内存 Map 实现。
- 不在 `M1` 中承诺跨重启恢复。
- 默认假设在 event loop 串行上下文中运行；如后续跨线程访问，必须重新定义并发约束。

### 6. routing

职责：

- 维护 Topic Filter 到订阅者集合的映射。
- 执行主题匹配，包括精确匹配与 `+`、`#` 通配符。
- 为发布链路返回目标订阅者列表。

建议对象：

- `TopicFilterMatcher`
- `SubscriptionRegistry`
- `RouteResult`

边界：

- `M1` 只考虑普通订阅，不考虑共享订阅。
- `M1` 只支持单机内存路由。
- 匹配与索引更新实现必须避免阻塞操作。

### 7. auth

职责：

- 提供鉴权扩展点。

`M1` 建议：

- 保留接口。
- 提供默认放行实现。

原因：

- 避免把连接流程写死。
- 不阻塞 `M1` 最小闭环。

### 8. observability

职责：

- 记录关键协议事件。
- 暴露连接数、订阅数、收发报文数等基础指标的扩展点。

`M1` 最小要求：

- 关键连接事件有日志。
- 协议拒绝路径有结构化记录点。

## 关键状态归属

### 连接级状态

归属：`connection`

包括：

- 当前网络连接是否存活。
- Keep Alive 计时信息。
- 当前连接状态。
- 当前连接关联的认证结果。

### 会话级状态

归属：`session`

`M1` 包括：

- `clientId`
- 当前订阅集
- 连接绑定关系

`M2+` 扩展：

- Session Expiry
- QoS 未完成流转状态
- 离线消息
- Will 配置

### 路由级状态

归属：`routing`

包括：

- Topic Filter 到订阅者集合的索引。
- 主题匹配规则实现。

## 建议调用方向

```text
transport -> codec -> protocol
protocol -> auth
protocol -> connection
protocol -> session
protocol -> routing
protocol -> observability
protocol -> transport
```

约束：

- `routing` 不反向依赖 `protocol`。
- `session` 不直接操作网络。
- `transport` 不直接决定协议返回码。

## M1 核心对象关系

```text
ClientConnection 1 -> 0..1 Session
Session 1 -> 0..n Subscription
SubscriptionRegistry 1 -> 0..n Subscription
PublishRequest 1 -> RouteResult -> 0..n Session
```

## M1 实现建议顺序

1. `codec` 与基础报文模型。
2. `transport` 与连接事件接入。
3. `ConnectHandler` 和 `connection/session` 最小闭环。
4. `SubscriptionRegistry` 与主题匹配。
5. `PublishHandler` 的 QoS 0 主链路。
6. `UnsubscribeHandler`、`DisconnectHandler`、Keep Alive。

## 待决策项

- `connection` 是否直接复用 Vert.x/Netty Channel 上下文，还是封装独立对象。
- Topic Filter 数据结构使用树结构还是“规范化后线性匹配”起步。
- 协议处理器采用单类分发还是“一个报文类型一个处理器”。
- 订阅信息同时保存在 `session` 与 `routing` 时如何保证一致性。
