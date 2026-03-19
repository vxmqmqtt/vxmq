# 文档导航

本文档目录用于承载 `vxmq` 项目的全部工程文档。除临时讨论外，项目的重要信息都应沉淀到这里。

## 目录说明

- `00-overview/`: 项目愿景、范围、术语。
- `01-requirements/`: 功能性与非功能性需求。
- `02-architecture/`: 架构设计、模块职责、核心流程。
- `03-protocol/`: MQTT 协议相关设计与实现说明。
- `04-api-and-config/`: 配置、管理接口、观测接口。
- `05-testing/`: 测试策略、兼容性与性能验证。
- `06-operations/`: 部署、运维、安全、故障处理。
- `07-project/`: 协作规范、路线图、里程碑、决策记录。

## 当前优先文档

1. `00-overview/vision.md`
2. `00-overview/scope.md`
3. `01-requirements/mqtt5-feature-matrix.md`
4. `01-requirements/compatibility.md`
5. `02-architecture/architecture-overview.md`
6. `07-project/collaboration.md`
7. `07-project/milestones.md`
8. `02-architecture/module-design.md`
9. `03-protocol/connect-flow.md`
10. `03-protocol/subscribe-flow.md`
11. `03-protocol/publish-flow.md`
12. `02-architecture/topic-routing.md`

## 文档约定

- 默认使用中文编写。
- 文档要区分“已确认”“待确认”“假设”。
- 重要架构决策应补充到 `07-project/decisions/`。
- 需求、设计、测试文档之间要互相链接，避免信息孤岛。
