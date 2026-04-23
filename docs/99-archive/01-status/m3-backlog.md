# M3 开发清单

本文档是 `M3 协议完整性与基础运维` 的可执行开发清单。阶段目标与出口定义查看 [`milestones.md`](milestones.md)，当前状态查看 [`../../01-status/current-status.md`](../../01-status/current-status.md)。

## 使用规则

- 本文档按“先后顺序 + 完成标准”组织，服务于具体实现推进。
- 条目状态使用：`未开始`、`进行中`、`已完成`。
- 任一条目标记为 `已完成` 前，必须具备对应自动化测试和文档同步。

## M3 执行优先级

1. QoS 2 状态机
2. 订阅增强能力（Subscription Options / Subscription Identifier）
3. MQTT 5 关键属性（User Property / Message Expiry / Flow Control 等）
4. 基础鉴权
5. 基础可观测性（健康检查、指标、日志诊断）

## 开发条目

| ID | 条目 | 当前状态 | 完成标准 |
| --- | --- | --- | --- |
| M3-01 | QoS 2 状态机（PUBREC/PUBREL/PUBCOMP） | 未开始 | 协议状态机闭环可用；包含重复包与重连场景单测/集成测试；更新协议文档 |
| M3-02 | Subscription Options（No Local / Retain As Published / Retain Handling） | 未开始 | 订阅存储与投递行为符合 MQTT 5 语义；保留消息下发行为与选项联动；测试覆盖主要组合 |
| M3-03 | Subscription Identifier | 未开始 | 订阅时可绑定标识并在下发中体现；去重与多订阅场景行为清晰；测试覆盖 |
| M3-04 | User Property 透传 | 未开始 | 入站/出站链路属性透传行为明确；不支持场景有显式限制；测试覆盖 |
| M3-05 | Message Expiry Interval | 未开始 | 消息过期语义落到在线/离线投递路径；过期后不再下发；测试覆盖 |
| M3-06 | Receive Maximum | 未开始 | 入站/出站流控边界可配置；超限行为明确；测试覆盖 |
| M3-07 | Maximum Packet Size | 未开始 | 包大小限制在连接协商与收发链路生效；超限行为明确；测试覆盖 |
| M3-08 | Response Topic / Correlation Data | 未开始 | 属性透传与可见性验证完成；文档补充请求-响应场景限制 |
| M3-09 | Payload Format Indicator / Content Type | 未开始 | 属性透传策略明确；无隐式内容校验副作用；测试覆盖 |
| M3-10 | 用户名密码鉴权（基础版） | 未开始 | CONNECT 鉴权入口落地；成功/失败路径可测试；失败断连语义与日志可观测 |
| M3-11 | 健康检查（Readiness / Liveness） | 未开始 | 关键组件状态暴露；异常状态可复现验证 |
| M3-12 | Metrics（连接数/会话数/消息速率等） | 未开始 | 关键指标可拉取；指标命名和标签规范固定；测试或验收脚本可验证 |
| M3-13 | 日志与诊断增强 | 未开始 | 关键协议事件具备可定位日志；错误与断连路径诊断信息完整；文档补充排障入口 |

## 阶段验收门槛（M3）

- `M3-01`、`M3-02`、`M3-03`、`M3-10`、`M3-11`、`M3-12` 至少达到 `已完成`。
- MQTT 5 关键属性条目（`M3-04` 到 `M3-09`）至少完成其中 4 项，并在特性矩阵中同步状态。
- 所有 `已完成` 条目必须有对应测试与文档变更，且可在一次回归中验证通过。
