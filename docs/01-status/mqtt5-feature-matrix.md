# MQTT 特性矩阵

本文档只回答一个问题：项目当前对哪些能力支持到什么程度。阶段规划看 [`milestones.md`](milestones.md)，历史验收看 [`m1-acceptance-checklist.md`](m1-acceptance-checklist.md)。

## 状态定义

- `未开始`：尚未进入设计或实现。
- `设计中`：设计方向已确定，但尚未完成实现。
- `已实现`：代码已具备基础实现，但验证仍不充分。
- `已验证`：代码、测试和阶段结论已形成闭环。

## 特性矩阵

| 类别 | 能力 | MQTT 3.1.1 | MQTT 5 | 当前状态 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 连接管理 | CONNECT / CONNACK 基础流程 | 支持 | 支持 | 已验证 | 已覆盖空 `clientId` 分支与基础返回码 |
| 连接管理 | Keep Alive | 支持 | 支持 | 已验证 | 依赖 `vertx-mqtt` 内置超时处理 |
| 连接管理 | Client Identifier 规则 | 支持 | 支持 | 已验证 | 已支持自动分配与重复连接接管 |
| 连接管理 | Clean Start / Session Expiry | 不支持 | 不支持 | 未开始 | 计划进入 `M2` |
| 连接管理 | Disconnect 语义 | 基础支持 | 基础支持 | 已验证 | 已覆盖主动断连、接管断连与异常断连 |
| 发布订阅 | SUBSCRIBE / SUBACK | 支持 | 支持 | 已验证 | 已覆盖合法与非法 Topic Filter |
| 发布订阅 | UNSUBSCRIBE / UNSUBACK | 支持 | 支持 | 已验证 | 已覆盖 MQTT 5 reason code |
| 发布订阅 | PUBLISH QoS 0 主链路 | 支持 | 支持 | 已验证 | 已具备最小端到端闭环 |
| 发布订阅 | Topic Filter / Wildcard | 支持 | 支持 | 已验证 | 已支持 `+` 与 `#` |
| 发布订阅 | Shared Subscription | 不支持 | 不支持 | 未开始 | 后置能力 |
| QoS | QoS 1 | 不支持 | 不支持 | 未开始 | 计划进入 `M2` |
| QoS | QoS 2 | 不支持 | 不支持 | 未开始 | 计划进入 `M2` |
| 状态管理 | Session State | 不支持 | 不支持 | 未开始 | 计划进入 `M2` |
| 状态管理 | Retained Message | 不支持 | 不支持 | 未开始 | 计划进入 `M2` |
| 状态管理 | Will Message | 不支持 | 不支持 | 未开始 | 计划进入 `M2` |
| MQTT 5 属性 | Session Expiry Interval | 不适用 | 不支持 | 未开始 | 计划进入 `M2` |
| MQTT 5 属性 | User Property | 不适用 | 不支持 | 未开始 | 计划进入 `M3` |
| MQTT 5 属性 | Message Expiry Interval | 不适用 | 不支持 | 未开始 | 计划进入 `M3` |
| MQTT 5 属性 | Receive Maximum | 不适用 | 不支持 | 未开始 | 计划进入 `M3` |
| MQTT 5 属性 | Response Topic / Correlation Data | 不适用 | 不支持 | 未开始 | 计划进入 `M3` |
| MQTT 5 属性 | Payload Format Indicator / Content Type | 不适用 | 不支持 | 未开始 | 计划进入 `M3` |
| MQTT 5 属性 | Topic Alias | 不适用 | 不支持 | 未开始 | 计划进入 `M5` |
| 安全 | 用户名密码鉴权 | 不支持 | 不支持 | 未开始 | 计划进入 `M3` |
| 安全 | TLS | 不支持 | 不支持 | 未开始 | 计划进入 `M4` |
| 运维 | 健康检查 | 不支持 | 不支持 | 未开始 | 计划进入 `M3` |
| 运维 | Metrics | 不支持 | 不支持 | 未开始 | 计划进入 `M3` |
| 运维 | 日志与追踪基础 | 基础支持 | 基础支持 | 设计中 | 当前仅有最小事件日志 |
| 可靠性 | Broker 重启恢复 | 不支持 | 不支持 | 未开始 | 计划进入 `M4` |
| 可靠性 | 离线消息恢复 | 不支持 | 不支持 | 未开始 | 计划进入 `M4` |
| 验证 | 互操作性测试基线 | 基础支持 | 基础支持 | 设计中 | 当前已有最小 MQTT 集成测试 |
| 验证 | 性能基线测试 | 不支持 | 不支持 | 未开始 | 计划进入 `M4` |

## 使用规则

- 本文档只维护“能力状态”，不维护阶段总结、实现理由或验收过程。
- 若某项能力状态变化，应同步补充关联测试或关联文档，而不是只改表格。
- 若某项能力存在阶段性降级，应在备注中写清限制边界。
