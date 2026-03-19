# M1 验收清单

本文档用于对照 `07-project/milestones.md` 的 `M1 最小闭环` 出口标准，判断当前实现是否可以结束 `M1` 并进入 `M2`。

状态说明：

- `已满足`: 已有实现、测试和文档支撑。
- `部分满足`: 已有实现，但测试、文档或边界说明仍不充分。
- `未满足`: 尚未达到 `M1` 要求。

## 交付内容检查

| 项目 | 目标 | 当前状态 | 依据 |
| --- | --- | --- | --- |
| CONNECT / CONNACK 基础处理 | 支持 MQTT 3.1.1 / MQTT 5 基础连接、空 `clientId` 分支、重复连接接管 | 已满足 | `DefaultProtocolEngineTest`、`VertxMqttBrokerTransportIntegrationTest`、`connect-flow.md` |
| SUBSCRIBE / SUBACK | 订阅注册、Topic Filter 校验、MQTT 3.1.1 / MQTT 5 返回路径 | 已满足 | `DefaultProtocolEngineTest`、`subscribe-flow.md` |
| UNSUBSCRIBE / UNSUBACK | 取消订阅、MQTT 5 reason code、退订后不再投递 | 已满足 | `DefaultProtocolEngineTest`、`VertxMqttBrokerTransportIntegrationTest`、`subscribe-flow.md` |
| PUBLISH 主链路 | QoS 0 入站发布、订阅匹配、在线投递、非法路径拒绝 | 已满足 | `DefaultProtocolEngineTest`、`VertxMqttBrokerTransportIntegrationTest`、`publish-flow.md` |
| QoS 0 | 最小消息通路打通 | 已满足 | 单元测试与集成测试均已覆盖 |
| Topic Filter / Wildcard | `+` / `#` 匹配与非法过滤器拒绝 | 已满足 | `DefaultTopicMatcherTest`、`topic-routing.md` |
| 基础断连处理 | 正常断连、接管断连、异常发布断连、连接关闭清理 | 已满足 | `DefaultProtocolEngineTest`、`VertxMqttBrokerTransportIntegrationTest` |
| Keep Alive | 1.5 倍超时关闭、PINGREQ/PINGRESP 正常工作 | 已满足 | `VertxMqttBrokerTransportIntegrationTest`，依赖 `vertx-mqtt` 内置机制 |

## 出口标准检查

| 标准 | 当前状态 | 说明 |
| --- | --- | --- |
| 至少可以完成“连接 -> 订阅 -> 发布 -> 收到消息 -> 断连”的稳定闭环 | 已满足 | 已有真实 MQTT 集成测试覆盖完整闭环 |
| 关键协议交互有自动化集成测试 | 已满足 | 已覆盖连接成功、重复 `clientId` 接管、订阅/退订、发布投递、Keep Alive 超时、非法 QoS 断连 |
| README 与文档能够指导后续功能继续落地 | 已满足 | README、特性矩阵、协议流程、里程碑与 ADR 已同步到当前状态 |

## 非阻塞缺口

以下事项不再阻塞 `M1`，转入 `M2` 或后续阶段处理：

- QoS 1 / QoS 2。
- Session State、Clean Start / Session Expiry。
- Retained Message。
- Will Message。
- MQTT 5 更多属性与安全能力。

## 结论

结论：`M1` 验收条件已满足，可以判定 `M1 最小闭环` 完成。

建议后续动作：

1. 将当前阶段在项目沟通中统一表述为“`M1` 已完成，准备进入 `M2`”。
2. 以 `Session State / Clean Start / Session Expiry` 为 `M2` 第一批输入。
3. 在进入 `M2` 前，先补对应设计文档，避免会话语义与可靠性实现返工。
