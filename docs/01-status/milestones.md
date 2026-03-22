# 里程碑规划

本文档定义项目阶段规划。当前状态结论看 [`current-status.md`](current-status.md)，具体能力完成度看 [`mqtt5-feature-matrix.md`](mqtt5-feature-matrix.md)。

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

## M2 核心可靠性

目标：补齐会话与消息可靠性核心语义。

阶段内容：

- Session State
- Clean Start / Session Expiry
- QoS 1 / QoS 2
- Retained Message
- Will Message

阶段出口：

- 异常断连、重连、会话恢复和消息可靠性行为有稳定定义。
- 关键可靠性语义具备自动化验证。

## M3 协议增强与基础运维

目标：补齐 MQTT 5 关键属性，并建立基础鉴权与观测能力。

阶段内容：

- User Property
- Message Expiry Interval
- Receive Maximum
- Response Topic / Correlation Data
- Payload Format Indicator / Content Type
- 用户名密码鉴权
- 健康检查、指标、日志诊断基础

## M4 稳定性与验证闭环

目标：建立恢复能力、互操作性验证和性能基线。

阶段内容：

- 持久化策略首版
- Broker 重启恢复
- 离线消息恢复
- TLS
- 互操作性测试基线
- 性能基线测试

## M5 高级特性与生产化增强

目标：补齐高级能力，并明确生产化差距。

阶段内容：

- Shared Subscription
- Topic Alias
- 其余经确认需要纳入的高级增强项

## 使用规则

- 里程碑只定义阶段目标与出口，不承担当前阶段总结职责。
- 里程碑变更时，应同步检查特性矩阵与当前状态文档是否一致。
