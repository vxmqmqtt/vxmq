# 里程碑规划

本文档定义项目推进节奏。功能清单看 `01-requirements/mqtt5-feature-matrix.md`，这里聚焦阶段目标与交付边界。

## M1 最小闭环

目标：形成一个最小可运行的单机 MQTT Broker 主链路。

交付内容：

- CONNECT / CONNACK 基础处理。
- SUBSCRIBE / SUBACK、UNSUBSCRIBE / UNSUBACK。
- PUBLISH 主链路。
- QoS 0。
- Topic Filter / Wildcard。
- 基础断连处理与 Keep Alive。

完成标准：

- 至少可以完成“连接 -> 订阅 -> 发布 -> 收到消息 -> 断连”的稳定闭环。
- 关键协议交互有自动化集成测试。
- README 与文档能够指导后续功能继续落地。

## M2 核心可靠性

目标：补齐会话与消息可靠性核心语义。

交付内容：

- Session State。
- Clean Start / Session Expiry。
- QoS 1 / QoS 2。
- Retained Message。
- Will Message。

完成标准：

- 重连、异常断开、未完成消息恢复等行为有定义且可验证。
- 可靠性核心逻辑不依赖手工验证。

## M3 协议增强与基础运维

目标：让 Broker 从“能跑”进入“可观测、可定位、支持更多 MQTT 5 语义”阶段。

交付内容：

- User Property。
- Message Expiry Interval。
- Response Topic / Correlation Data。
- Payload Format Indicator / Content Type。
- Receive Maximum。
- 用户名密码鉴权。
- 健康检查、指标、基础日志诊断。

完成标准：

- 关键属性的行为可通过自动化测试证明。
- 运行中的核心状态具备基础观察手段。

## M4 稳定性与验证闭环

目标：建立恢复能力、兼容性验证和性能基线。

交付内容：

- 持久化策略首版。
- Broker 重启恢复。
- 离线消息恢复。
- TLS。
- 互操作性测试基线。
- 性能基线测试。

完成标准：

- 能够回答“系统重启后会怎样”“与外部客户端是否兼容”“当前性能大致如何”。

## M5 高级特性与生产化增强

目标：补齐高级能力并明确生产化差距。

交付内容：

- Shared Subscription。
- Topic Alias。
- 其余经确认需要纳入的高级增强项。

完成标准：

- 高级特性有明确实现状态。
- 对未实现项有清晰的延后理由，不留模糊地带。

## 执行规则

- 每个里程碑开始前，先确认输入条件与边界。
- 每个里程碑完成后，必须更新特性矩阵状态。
- 里程碑内若新增需求，先判断是否影响边界，必要时重新定级。
