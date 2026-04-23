# 里程碑规划

本文档定义项目阶段规划。当前状态结论看 [`../../01-status/current-status.md`](../../01-status/current-status.md)，具体能力完成度看 [`../../01-status/mqtt5-feature-matrix.md`](../../01-status/mqtt5-feature-matrix.md)。
`M3` 的执行拆解清单看 [`m3-backlog.md`](m3-backlog.md)。

## M1 最小闭环

目标：形成一个最小可运行、可验证的单机 MQTT Broker 主链路。

阶段内容：

- CONNECT / CONNACK
- SUBSCRIBE / SUBACK
- UNSUBSCRIBE / UNSUBACK
- PUBLISH QoS 0
- Topic Filter / Wildcard
- 基础断连与 Keep Alive
- 单元测试与端到端集成测试基础闭环

阶段出口：

- 客户端可稳定完成连接、订阅、发布、取消订阅与断连闭环。
- 关键主链路具备自动化测试支撑。
- 文档、测试与实现状态一致。

## M2 会话与可靠性基础

目标：补齐“同一客户端跨断连继续工作”所需的核心语义，形成可靠性基础版本。

阶段内容：

- Session State
- MQTT 3.1.1 `Clean Session`
- MQTT 5 `Clean Start / Session Expiry`
- 订阅树 / 路由索引重构
- 内存态离线消息与重连投递
- QoS 1
- Retained Message
- Will Message

阶段出口：

- 客户端在断连、重连和会话延续场景下行为可预测。
- QoS 1、保留消息和遗嘱消息具备自动化验证。
- 路由层具备承载后续订阅选项、共享订阅和更大订阅规模的内部结构基础。
- `M2` 的可靠性语义先以单机、内存态闭环成立，不要求跨重启恢复。

## M3 协议完整性与基础运维

目标：补齐 QoS 2、主要 MQTT 5 属性与基础运行可观测能力，让 Broker 从“可靠基础版”进入“协议能力持续补齐”阶段。

阶段内容：

- QoS 2
- Subscription Options
- Subscription Identifier
- User Property
- Message Expiry Interval
- Receive Maximum
- Maximum Packet Size
- Response Topic / Correlation Data
- Payload Format Indicator / Content Type
- 用户名密码鉴权
- 健康检查、指标、日志诊断基础

阶段出口：

- QoS 2 与关键 MQTT 5 属性具备清晰实现和自动化验证。
- 运行中的连接、订阅、消息流转具备基础观察能力。
- 常见协议问题可以通过日志和指标进行初步定位。

## M4 持久化与验证闭环

目标：建立持久化、跨重启恢复、互操作性和性能基线，回答“系统是否稳、与外部是否兼容”。

阶段内容：

- 持久化策略首版
- Broker 重启恢复
- 跨重启的离线消息恢复
- TLS
- 互操作性测试基线
- 性能基线测试
- 服务端能力声明相关收敛

阶段出口：

- 系统重启后关键状态与关键消息行为可预测。
- 至少建立一轮与外部 MQTT 客户端/工具的固定互操作性验证。
- 性能和资源占用有可复现基线，而不是凭感觉评估。

## M5 高级特性与生产化增强

目标：补齐高级 MQTT 5 能力，并明确生产化能力差距。

阶段内容：

- Shared Subscription
- Topic Alias
- Enhanced Authentication / AUTH
- Will Delay Interval
- 其余经确认需要纳入的高级增强项

阶段出口：

- 高级特性有明确实现状态与验证结果。
- 对暂未实现的高级项有清晰延后理由，不保留模糊承诺。

## 使用规则

- 里程碑只定义阶段目标与出口，不承担当前阶段总结职责。
- 阶段规划必须体现依赖顺序，避免把依赖前置能力放到后续阶段。
- 里程碑变更时，应同步检查特性矩阵与当前状态文档是否一致。
