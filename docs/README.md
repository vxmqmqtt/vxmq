# 文档导航

`docs/` 是项目文档的唯一入口。重构后的文档体系按三类职责组织：

- `00-foundation`：项目定位、范围、协议兼容原则和术语。
- `01-status`：当前状态、路线图和能力矩阵。
- `02-architecture` / `03-protocol`：长期系统真相。
- `07-project` / `99-archive`：协作约束、ADR 与历史记录。

## 我想知道项目做什么

- 项目定位与范围：[`00-foundation/project.md`](00-foundation/project.md)
- 协议兼容原则：[`00-foundation/compatibility.md`](00-foundation/compatibility.md)
- 术语表：[`00-foundation/glossary.md`](00-foundation/glossary.md)

## 我想知道当前做到哪

- 当前状态：[`01-status/current-status.md`](01-status/current-status.md)
- MQTT 能力矩阵：[`01-status/mqtt5-feature-matrix.md`](01-status/mqtt5-feature-matrix.md)

## 我想知道接下来做什么

- 路线图与当前活跃里程碑：[`01-status/roadmap.md`](01-status/roadmap.md)

## 我想知道系统怎么组织

- 系统设计与模块边界：[`02-architecture/system-design.md`](02-architecture/system-design.md)
- 状态归属与路由索引：[`02-architecture/state-and-routing.md`](02-architecture/state-and-routing.md)

## 我想知道协议行为是什么

- 连接建立、恢复与断连：[`03-protocol/connection-lifecycle.md`](03-protocol/connection-lifecycle.md)
- 发布、订阅、匹配、QoS、retained、will：[`03-protocol/message-delivery.md`](03-protocol/message-delivery.md)

## 我想知道为什么这样设计

- 协作规范：[`07-project/collaboration.md`](07-project/collaboration.md)
- ADR 列表：[`07-project/decisions/`](07-project/decisions/)

## 我想看历史记录

- 历史归档入口：[`99-archive/README.md`](99-archive/README.md)

## 使用规则

- 当前状态只在 `01-status/` 维护。
- 长期系统真相只在 `00-foundation/`、`02-architecture/`、`03-protocol/` 维护。
- ADR 负责记录关键决策原因，不承担当前状态入口职责。
- 归档文档用于保留历史结论，不作为当前真相引用源。
