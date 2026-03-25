# MQTT 特性矩阵

本文档只回答一个问题：项目当前对哪些能力支持到什么程度。阶段规划看 [`milestones.md`](milestones.md)，历史验收看 [`m1-acceptance-checklist.md`](m1-acceptance-checklist.md)。

## 状态定义

- `未开始`：尚未进入设计或实现。
- `设计中`：设计方向已确定，但尚未完成实现。
- `已实现`：代码已具备基础实现，但验证仍不充分。
- `已验证`：代码、测试和阶段结论已形成闭环。

## 特性矩阵

| 类别 | 能力 | 目标阶段 | MQTT 3.1.1 | MQTT 5 | 当前状态 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 连接管理 | CONNECT / CONNACK 基础流程 | M1 | 支持 | 支持 | 已验证 | 已覆盖空 `clientId` 分支与基础返回码 |
| 连接管理 | Keep Alive | M1 | 支持 | 支持 | 已验证 | 依赖 `vertx-mqtt` 内置超时处理 |
| 连接管理 | Client Identifier 规则 | M1 | 支持 | 支持 | 已验证 | 已支持自动分配与重复连接接管 |
| 连接管理 | Persistent Session / Clean Session | M2 | 不支持 | 不适用 | 未开始 | 指 MQTT 3.1.1 的持久会话语义 |
| 连接管理 | Clean Start / Session Expiry | M2 | 不适用 | 不支持 | 未开始 | 指 MQTT 5 会话开启与过期语义 |
| 连接管理 | Disconnect 语义 | M1 | 基础支持 | 基础支持 | 已验证 | 已覆盖主动断连、接管断连与异常断连 |
| 连接管理 | Enhanced Authentication / AUTH | M5 | 不适用 | 不支持 | 未开始 | MQTT 5 高级认证流程 |
| 发布订阅 | SUBSCRIBE / SUBACK | M1 | 支持 | 支持 | 已验证 | 已覆盖合法与非法 Topic Filter |
| 发布订阅 | UNSUBSCRIBE / UNSUBACK | M1 | 支持 | 支持 | 已验证 | 已覆盖 MQTT 5 reason code |
| 发布订阅 | PUBLISH QoS 0 主链路 | M1 | 支持 | 支持 | 已验证 | 已具备最小端到端闭环 |
| 发布订阅 | Topic Filter / Wildcard | M1 | 支持 | 支持 | 已验证 | 已支持 `+` 与 `#` |
| 内部机制 | 订阅树 / 路由索引重构 | M2 | 不适用 | 不适用 | 未开始 | 这是少量例外的内部基础设施项，用于支撑后续订阅匹配与高级订阅能力 |
| 发布订阅 | Subscription Options | M3 | 不适用 | 不支持 | 未开始 | 包括 `No Local`、`Retain As Published`、`Retain Handling` |
| 发布订阅 | Subscription Identifier | M3 | 不适用 | 不支持 | 未开始 | MQTT 5 订阅标识 |
| 发布订阅 | Shared Subscription | M5 | 不适用 | 不支持 | 未开始 | 高级分发能力 |
| QoS | QoS 1 | M2 | 不支持 | 不支持 | 未开始 | 含入站、出站与 PUBACK 语义 |
| QoS | QoS 2 | M3 | 不支持 | 不支持 | 未开始 | 含 PUBREC / PUBREL / PUBCOMP 状态机 |
| 状态管理 | Session State | M2 | 不支持 | 不支持 | 未开始 | 会话视图从“连接绑定”扩展到完整会话语义 |
| 状态管理 | 离线消息与重连投递 | M2 | 不支持 | 不支持 | 未开始 | 指单机、内存态下的会话延续与消息恢复 |
| 状态管理 | Retained Message | M2 | 不支持 | 不支持 | 未开始 | 含覆盖、清除与订阅后下发 |
| 状态管理 | Will Message | M2 | 不支持 | 不支持 | 未开始 | 含异常断开触发条件 |
| 状态管理 | Will Delay Interval | M5 | 不适用 | 不支持 | 未开始 | MQTT 5 高级遗嘱属性 |
| MQTT 5 属性 | User Property | M3 | 不适用 | 不支持 | 未开始 | 透传与可见性验证 |
| MQTT 5 属性 | Message Expiry Interval | M3 | 不适用 | 不支持 | 未开始 | 与离线消息和过期策略联动 |
| MQTT 5 属性 | Receive Maximum | M3 | 不适用 | 不支持 | 未开始 | 流控相关 |
| MQTT 5 属性 | Maximum Packet Size | M3 | 不适用 | 不支持 | 未开始 | 连接协商与限制相关 |
| MQTT 5 属性 | Topic Alias | M5 | 不适用 | 不支持 | 未开始 | 高级优化能力 |
| MQTT 5 属性 | Response Topic / Correlation Data | M3 | 不适用 | 不支持 | 未开始 | 请求响应场景支撑 |
| MQTT 5 属性 | Payload Format Indicator / Content Type | M3 | 不适用 | 不支持 | 未开始 | 语义透传为主 |
| MQTT 5 属性 | 服务端能力声明 | M4 | 不适用 | 不支持 | 未开始 | 包括 `Maximum QoS`、`Retain Available`、`Wildcard Subscription Available`、`Shared Subscription Available` 等对外声明 |
| 安全 | 用户名密码鉴权 | M3 | 不支持 | 不支持 | 未开始 | 先做基础接入，再扩展高级认证 |
| 安全 | TLS | M4 | 不支持 | 不支持 | 未开始 | 与部署方式和证书管理联动 |
| 运维 | 健康检查 | M3 | 不支持 | 不支持 | 未开始 | Broker 就绪态与活跃态 |
| 运维 | Metrics | M3 | 不支持 | 不支持 | 未开始 | 连接数、消息速率、会话数等 |
| 运维 | 日志与追踪基础 | M3 | 基础支持 | 基础支持 | 设计中 | 当前仅有最小事件日志 |
| 可靠性 | 持久化策略首版 | M4 | 不支持 | 不支持 | 未开始 | 为跨重启恢复提供基础 |
| 可靠性 | Broker 重启恢复 | M4 | 不支持 | 不支持 | 未开始 | 关键状态和关键消息的跨重启行为 |
| 可靠性 | 跨重启离线消息恢复 | M4 | 不支持 | 不支持 | 未开始 | 区别于 `M2` 的内存态重连恢复 |
| 验证 | 互操作性测试基线 | M4 | 基础支持 | 基础支持 | 设计中 | 当前已有最小 MQTT 集成测试，后续需引入外部客户端验证 |
| 验证 | 性能基线测试 | M4 | 不支持 | 不支持 | 未开始 | 首先建立基线，不急于追求极限优化 |

## 使用规则

- 本文档只维护“能力状态”，不维护阶段总结、实现理由或验收过程。
- 特性粒度应尽量以“外部可感知能力”组织，不把所有单个属性机械拆成过细条目。
- 若某个内部机制会显著影响后续阶段实现顺序或架构稳定性，可作为例外显式列入矩阵。
- 若某项能力状态变化，应同步补充关联测试或关联文档，而不是只改表格。
- 若某项能力存在阶段性降级，应在备注中写清限制边界。
