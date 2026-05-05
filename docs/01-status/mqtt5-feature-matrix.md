# MQTT 特性矩阵

本文档只回答一个问题：项目当前对哪些能力支持到什么程度。路线图看 [`roadmap.md`](roadmap.md)，当前状态看 [`current-status.md`](current-status.md)。

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
| 连接管理 | Persistent Session / Clean Session | M2 | 支持 | 不适用 | 已验证 | 已覆盖新建、恢复、删除与订阅恢复 |
| 连接管理 | Clean Start / Session Expiry | M2 | 不适用 | 基础支持 | 已验证 | 已覆盖会话创建、恢复、懒清理与离线 QoS 1 恢复 |
| 连接管理 | Disconnect 语义 | M1 | 基础支持 | 基础支持 | 已验证 | 已覆盖主动断连、接管断连与异常断连 |
| 连接管理 | Enhanced Authn / AUTH | M5 | 不适用 | 不支持 | 未开始 | MQTT 5 高级认证流程 |
| 发布订阅 | SUBSCRIBE / SUBACK | M1 | 支持 | 支持 | 已验证 | 已覆盖合法与非法 Topic Filter |
| 发布订阅 | UNSUBSCRIBE / UNSUBACK | M1 | 支持 | 支持 | 已验证 | 已覆盖 MQTT 5 reason code |
| 发布订阅 | PUBLISH QoS 0 主链路 | M1 | 支持 | 支持 | 已验证 | 已具备最小端到端闭环 |
| 发布订阅 | Topic Filter / Wildcard | M1 | 支持 | 支持 | 已验证 | 已支持 `+` 与 `#` |
| 内部机制 | 订阅树 / 路由索引重构 | M2 | 不适用 | 不适用 | 已验证 | 已采用订阅树替换线性扫描索引，并补充基准对比 harness |
| 发布订阅 | Subscription Options | M3 | 不适用 | 支持 | 已验证 | 已覆盖 `No Local`、`Retain As Published`、`Retain Handling` 与 retained replay 联动 |
| 发布订阅 | Subscription Identifier | M3 | 不适用 | 支持 | 已验证 | 已支持单订阅、多订阅合并、retained replay 与离线恢复 |
| 发布订阅 | Shared Subscription | M5 | 不适用 | 不支持 | 未开始 | 高级分发能力 |
| QoS | QoS 1 | M2 | 基础支持 | 基础支持 | 已验证 | 已覆盖入站、出站、PUBACK 与离线恢复；不含后台重试 |
| QoS | QoS 2 | M3 | 基础支持 | 基础支持 | 已验证 | 已覆盖 PUBREC / PUBREL / PUBCOMP、重复包、离线恢复与 retained 重放；will QoS 2 延后 |
| 状态管理 | Session State | M2 | 基础支持 | 基础支持 | 已验证 | 当前已支持在线/离线/过期、离线队列与 QoS 1 inflight |
| 状态管理 | 离线消息与重连投递 | M2 | 基础支持 | 基础支持 | 已验证 | 当前为单机、内存态 QoS 1 恢复；不含跨重启恢复 |
| 状态管理 | Retained Message | M2 | 基础支持 | 基础支持 | 已验证 | 已覆盖写入、清除与订阅后下发；支持 QoS 2 retained 重放，不含高级 retain 属性 |
| 状态管理 | Will Message | M2 | 基础支持 | 基础支持 | 已验证 | 已覆盖 CONNECT 保存、显式断连抑制、异常关闭发布、Will User Property 与 retain/QoS 1 联动；will QoS 2 延后 |
| 状态管理 | Will Delay Interval | M5 | 不适用 | 不支持 | 未开始 | MQTT 5 高级遗嘱属性 |
| MQTT 5 属性 | User Property | M3 | 不适用 | 入站 CONNECT / SUBSCRIBE / UNSUBSCRIBE request properties 建模；PUBLISH / Will 透传 | 已验证 | 已覆盖 auth 可读取 CONNECT 属性、订阅/取消订阅 request 建模、在线投递、retained replay、离线恢复、QoS 2 延迟路由和 will 发布 |
| MQTT 5 属性 | Message Expiry Interval | M3 | 不适用 | 支持 | 已验证 | 已覆盖在线投递、retained replay、离线恢复、QoS 2 延迟路由和出站剩余 interval；Will Message Expiry 延后 |
| MQTT 5 属性 | Receive Maximum | M3 | 不适用 | 不支持 | 未开始 | 流控相关 |
| MQTT 5 属性 | Maximum Packet Size | M3 | 不适用 | 不支持 | 未开始 | 连接协商与限制相关 |
| MQTT 5 属性 | Topic Alias | M5 | 不适用 | 不支持 | 未开始 | 高级优化能力 |
| MQTT 5 属性 | Response Topic / Correlation Data | M3 | 不适用 | 不支持 | 未开始 | 请求响应场景支撑 |
| MQTT 5 属性 | Payload Format Indicator / Content Type | M3 | 不适用 | 不支持 | 未开始 | 语义透传为主 |
| MQTT 5 属性 | 服务端能力声明 | M4 | 不适用 | 不支持 | 未开始 | 包括 `Maximum QoS`、`Retain Available`、`Wildcard Subscription Available`、`Shared Subscription Available` 等对外声明 |
| 安全 | 用户名密码认证与鉴权链 | M3 | 基础支持 | 基础支持 | 已验证 | 配置驱动 static username/password；未启用认证资源时 permit-all；鉴权链已接入 CONNECT Will / SUBSCRIBE / PUBLISH，当前无 ACL 规则 |
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
