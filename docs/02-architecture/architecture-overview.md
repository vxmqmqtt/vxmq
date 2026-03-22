# 架构概览

## 设计目标

架构设计应满足以下原则：

1. 协议语义与工程实现分层清晰。
2. 核心状态有明确归属，避免隐式共享状态扩散。
3. 便于后续引入持久化、认证鉴权、集群和观测能力。
4. 便于测试，每个核心模块都应可独立验证。
5. 主链路遵循响应式、非阻塞、event-loop 友好的实现原则。

## 已确定实现约束

- `M1` 传输栈选择 Vert.x MQTT server，见 `../07-project/decisions/0004-m1-transport-stack-vertx-mqtt.md`。
- `M1` 主链路采用响应式、非阻塞、event-loop 模型，见 `../07-project/decisions/0005-m1-reactive-event-loop-model.md`。

## 当前核心模块

当前代码中已经落地的核心模块：

- `transport`: 网络连接、编解码、连接生命周期钩子。
- `protocol`: MQTT 报文校验、协议状态流转、返回码与属性处理。
- `session`: 客户端会话、订阅集与在线关联关系。
- `routing`: Topic 匹配与订阅索引。
- `auth`: 认证与授权扩展点。
- `observability`: 日志与诊断记录点。

## 后续扩展模块

以下模块或能力将在 `M2+` 再引入，不属于当前代码现状：

- `message-store`: 保留消息、离线消息、持久化抽象。
- `admin`: 管理接口与运行时查询能力。
- 健康检查、指标与更完整的运维观测能力。

## 核心流程

### 连接流程

1. 连接建立。
2. 报文解码与 CONNECT 校验。
3. 鉴权与客户端标识处理。
4. 会话恢复或新建。
5. 下发 CONNACK。
6. 进入正常收发阶段。

### 发布流程

1. 接收 PUBLISH。
2. 校验 Topic、QoS、属性与会话状态。
3. 路由到匹配订阅者。
4. 对当前 `M1` 仅执行 QoS 0 投递。

`M2+` 将继续补：

- QoS 1 / QoS 2 状态写入与握手。
- Retained Message。
- 离线消息。

### 断连流程

1. 接收 DISCONNECT 或探测异常断连。
2. 处理会话过期策略。
3. 判断是否触发遗嘱消息。
4. 清理连接级资源。

## 当前待决策项

- 内部事件驱动模型与同步调用边界如何划分。
- 会话状态先以内存实现还是直接抽象持久化接口。
- Topic Tree 是否在 `M2` 引入独立数据结构。
- QoS 2 状态机的内部表示形式。

## 相关文档

- `module-design.md`: `M1` 模块职责、状态归属与调用方向。
- `topic-routing.md`: `M1` 的 Topic Filter 匹配规则与路由索引设计。
- `../03-protocol/connect-flow.md`: `M1` 的 CONNECT / CONNACK 处理流程。
- `../03-protocol/subscribe-flow.md`: `M1` 的订阅与取消订阅处理流程。
- `../03-protocol/publish-flow.md`: `M1` 的发布与路由处理流程。
- `../07-project/decisions/0004-m1-transport-stack-vertx-mqtt.md`: `M1` 的传输栈选择。
- `../07-project/decisions/0005-m1-reactive-event-loop-model.md`: `M1` 的编程范式与线程模型。
