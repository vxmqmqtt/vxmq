# 架构概览

本文档描述 `vxmq` 的长期架构骨架，强调系统分层、模块边界和演进方向。当前阶段结论统一查看 [`../01-status/current-status.md`](../01-status/current-status.md)。

## 设计目标

- 保持协议主链路与 MQTT 规范语义对齐。
- 让 Quarkus 负责生命周期、配置和依赖注入，让 Broker 核心逻辑保持清晰边界。
- 在单机、内存态的实现基础上，为后续会话状态、QoS 可靠性和持久化能力预留扩展空间。
- 让响应式传输层与核心协议决策层分离，避免把网络 API 直接扩散到所有模块。

## 已确定的技术约束

- 传输栈使用 `Vert.x MQTT server`。
- 主链路采用响应式、非阻塞、event-loop 模型。
- 在 Quarkus 中接入 Vert.x 扩展时，优先使用 Mutiny 变体。

## 系统分层

### 宿主层

- `bootstrap`
- `config`

职责：对接 Quarkus 生命周期、加载配置、启动和停止 Broker。

### 传输层

- `transport`

职责：管理 MQTT endpoint、接收网络事件、完成协议报文与内部模型之间的桥接，并负责实际出站写回。

### 协议与领域层

- `protocol`
- `session`
- `routing`
- `auth`

职责：承载协议决策、连接与会话状态、订阅管理、Topic 匹配与鉴权扩展点。

### 观测层

- `observability`

职责：记录主链路关键事件，为后续日志、指标和诊断能力提供统一出口。

## 当前已落地模块

- `bootstrap`: Quarkus 启停桥接
- `config`: Broker 运行参数
- `transport`: 基于 Mutiny Vert.x MQTT 的服务端接入
- `protocol`: CONNECT、SUBSCRIBE、UNSUBSCRIBE、PUBLISH、DISCONNECT 的当前阶段决策
- `session`: 内存态会话视图
- `routing`: 内存态订阅索引与 Topic 匹配
- `auth`: 当前为最小放行实现
- `observability`: 当前为最小日志事件输出

## 规划中的扩展方向

- 会话持久化与恢复
- QoS 1 / QoS 2 状态机
- Retained Message
- Will Message
- 更完整的鉴权与 ACL
- 健康检查、指标和运维接口

## 模块协作主线

### 连接建立

1. `transport` 接收 CONNECT 并转换为内部 `ConnectRequest`
2. `protocol` 校验协议、鉴权、解析 `clientId` 并给出接管决策
3. `session` 与 `connectionRegistry` 更新当前连接归属
4. `transport` 按版本差异返回 CONNACK，并在必要时关闭旧连接

### 发布订阅

1. `transport` 接收 SUBSCRIBE / UNSUBSCRIBE / PUBLISH
2. `protocol` 完成语义校验并更新 `session`、`routing`
3. `routing` 解析命中订阅集合
4. `transport` 根据在线 endpoint 执行实际消息投递

### 断连

1. `transport` 接收主动断连、连接关闭或 Keep Alive 超时事件
2. `protocol` 更新连接和会话状态
3. `transport` 清理在线 endpoint 索引

## 相关文档

- [`module-design.md`](module-design.md)
- [`topic-routing.md`](topic-routing.md)
- [`../03-protocol/connect-flow.md`](../03-protocol/connect-flow.md)
- [`../03-protocol/subscribe-flow.md`](../03-protocol/subscribe-flow.md)
- [`../03-protocol/publish-flow.md`](../03-protocol/publish-flow.md)
