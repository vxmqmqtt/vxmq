# 状态归属与路由索引

本文档定义 `vxmq` 当前的状态归属、路由索引和相关边界。它回答“哪些状态由谁拥有、消息如何在这些状态之间流转、routing 与 retained 如何协作”。

边界：本文只描述内部状态和索引真相；协议报文处理顺序和对客户端可见的行为见 [`connection-lifecycle.md`](connection-lifecycle.md) 和 [`message-delivery.md`](message-delivery.md)。

## 总体原则

- 会话真相在 `session`，而不是 `routing`。
- 路由匹配索引在 `routing`，它是派生视图，不是订阅唯一真相。
- Retained 真相在 `retained`，不并入会话或路由索引。
- Will 属于会话状态的一部分，但发布时复用普通消息主链路。

## 会话模型

### 会话与连接的关系

- 连接是网络层对象，会话是与 `clientId` 绑定的逻辑状态集合。
- 同一会话可以跨多次连接延续。
- 持久会话在断连后可保留订阅、离线队列、QoS 1 inflight 和基础 will 状态。

### 当前会话字段

当前 `ClientSession` 至少承载：

- `clientId`
- 当前绑定连接 ID
- 是否持久
- 会话过期配置与到期时间
- 订阅集合
- `queuedMessages`
- `inflightMessages`
- `willMessage`

### 在线、离线与过期

- 在线会话：
  - 当前存在活跃连接，订阅变更和消息投递可以直接落到在线连接。
- 离线会话：
  - 当前无活跃连接，但持久会话仍保留订阅与离线 QoS 1 数据。
- 过期会话：
  - 已达到过期条件，后续会在访问或重建时被懒清理。

## 离线消息与 QoS 1 状态

每个持久会话当前维护两类与 QoS 1 相关的关键状态：

- `queuedMessages`
  - 该会话离线期间积压、等待恢复投递的 QoS 1 消息。
- `inflightMessages`
  - 已发送给订阅端、正在等待 `PUBACK` 的 QoS 1 消息。
- `pendingOutboundMessages`
  - 在线连接仍存在但 MQTT 5 `Receive Maximum` 窗口已满时，等待后续下发的 QoS 1 / QoS 2 消息。

当前语义：

- 在线目标会话收到 QoS 1 消息后，会进入 inflight 跟踪。
- 若目标会话的出站 receive window 已满，QoS 1 / QoS 2 消息先进入 pending 队列，待 `PUBACK` / `PUBCOMP` 释放窗口后继续下发。
- 若连接在消息确认前关闭，这些 inflight 消息会回退为离线队列。
- 会话恢复时，离线队列中的 QoS 1 消息会重新下发，并重新进入 inflight。

## Will 状态归属

- 会话真相中的 `willMessage` 保存当前会话生效中的基础 will。
- `ClientConnection` 会额外保存一份当前连接自己的 will 快照，用于连接接管和异常关闭时按连接维度触发。
- 显式 `DISCONNECT` 会清除会话与连接上的 will。
- 网络断开、Keep Alive 超时、协议错误断连和连接接管导致的旧连接关闭都可能触发 will 发布。

## 路由索引模型

### 核心结论

- 订阅树是 `routing` 的派生索引，不是订阅真相。
- 当前 `SubscriptionBinding` 以终结绑定的方式挂在树上，已为 Subscription Options、Subscription Identifier 和共享订阅预留扩展位。
- 同一客户端命中多个重叠订阅时仍只投递一次，并按更高 `grantedQos` 计算最终投递 QoS。

### 节点结构

每个订阅树节点包含：

- `exactChildren`
- `singleLevelWildcardChild`
- `terminalBindings`
- `multiLevelWildcardBindings`

### 匹配规则

- 匹配过程中同时检查当前节点上的 `multiLevelWildcardBindings`。
- 然后按当前 topic level 继续遍历精确子节点和 `+` 子节点。
- Topic path 结束后再检查 `terminalBindings`。

### 并发策略

- 当前主线路由索引已采用 `snapshot / copy-on-write` 方案完成并发安全落地。
- 读路径基于不可变快照，避免被写操作阻塞。
- 写路径允许通过内部批量 snapshot 重建减少 churn 成本。
- 原有 benchmark 和评估候选实现保留为独立评估套件，不作为运行时主线真相。

## Retained 模型

### 真相归属

- Retained Message 不属于 `session`，也不属于 `routing`。
- `retained` 保存按 Topic Name 建立的 retained 真相，用于回答“某个 Topic Filter 订阅后应立即收到什么”。

### 当前 retained 字段

当前 `RetainedMessage` 至少包含：

- `topicName`
- `payload`
- `qos`
- `retain`

### 当前 retained 规则

- `retain=true` 且 payload 非空时，写入或覆盖 retained store。
- `retain=true` 且 payload 为空时，删除对应 Topic Name 的 retained 记录。
- 订阅成功后，Broker 使用 Topic Filter 查询 retained store，并在 SUBACK 之后立即下发命中的 retained 消息。
- retained store 保存原始 retained 发布的 QoS，下发 QoS 取 `min(retained.qos, grantedQos)`。

## 模块边界

- `session`
  - 保存会话真相、订阅集合、离线 QoS 1、基础 will。
- `routing`
  - 保存订阅索引与匹配路径。
- `retained`
  - 保存 retained 真相并支持订阅后查询。
- `transport`
  - 不感知订阅树或 retained 存储细节，只消费协议层返回的结果。

## 当前实现边界

- 当前实现是单机、内存态状态模型。
- 当前已支持 QoS 1 的离线积压、恢复投递和 inflight 跟踪。
- 当前已支持基础 retained 和基础 will 语义。
- 当前不支持 QoS 2。
- 当前不支持 retained、会话和 will 的跨重启恢复。
- 当前不支持 `Will Delay Interval`、Subscription Options、Subscription Identifier 和共享订阅。
