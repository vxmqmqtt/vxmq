# M2 验收清单

本文档是 `M2 会话与可靠性基础` 的历史验收记录，用于保留阶段封板结论。当前项目状态统一查看 [`current-status.md`](current-status.md)。

## 交付内容检查

- Session State：已完成
- MQTT 3.1.1 `Clean Session`：已完成
- MQTT 5 `Clean Start / Session Expiry`：已完成
- 订阅树 / 路由索引重构：已完成
- 内存态离线消息与重连投递：已完成
- QoS 1：已完成
- Retained Message：已完成
- Will Message：已完成

## 出口标准检查

- 客户端在断连、重连与会话延续场景下行为可预测：通过
- QoS 1、Retained Message 与 Will Message 具备自动化验证：通过
- 路由层已经形成可支撑后续订阅选项和共享订阅演进的内部结构基础：通过
- `M2` 的可靠性语义已在单机、内存态范围内形成闭环：通过

## 非阻塞缺口

以下能力不是 `M2` 完成的阻塞项，但明确留待后续里程碑：

- QoS 2
- Subscription Options
- Subscription Identifier
- 用户名密码鉴权
- 健康检查、指标与日志诊断增强
- TLS
- 持久化与跨重启恢复能力

## 结论

`M2 会话与可靠性基础` 已完成，可以作为后续 `M3` 协议完整性与基础运维阶段的起点。
