# M1 验收清单

本文档是 `M1 最小闭环` 的历史验收记录，用于保留阶段封板结论。当前项目状态统一查看 [`current-status.md`](current-status.md)。

## 交付内容检查

- CONNECT / CONNACK 基础处理：已完成
- MQTT 3.1.1 与 MQTT 5 的基础连接差异处理：已完成
- SUBSCRIBE / SUBACK：已完成
- UNSUBSCRIBE / UNSUBACK：已完成
- PUBLISH QoS 0 主链路：已完成
- Topic Filter / Wildcard：已完成
- 基础断连语义：已完成
- Keep Alive：已完成
- 单元测试与 MQTT 集成测试闭环：已完成

## 出口标准检查

- 连接 -> 订阅 -> 发布 -> 收到消息 -> 断连的最小闭环：通过
- 关键协议主链路具备自动化测试：通过
- README、docs 导航和阶段结论一致：通过

## 非阻塞缺口

以下能力不是 `M1` 完成的阻塞项，但明确留待后续里程碑：

- Session State
- QoS 1 / QoS 2
- Retained Message
- Will Message
- 用户名密码鉴权
- TLS
- 持久化与恢复能力

## 结论

`M1 最小闭环` 已完成，可以作为后续 `M2` 及之后里程碑的基础版本。
