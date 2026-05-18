# ADR-0005 M1 阶段的编程范式与线程模型

- 状态：已接受
- 日期：2026-03-14

## 背景

在 Quarkus 中，Broker 主链路可以选择不同执行模型。对于 `vxmq`，当前需要明确两个问题：

1. `M1` 主链路采用响应式还是命令式实现思路。
2. `M1` 主链路以什么线程模型运行。

项目当前约束：

- 传输栈已确定为 Vert.x MQTT server。
- Broker 主链路是高频网络 I/O、协议解析、状态机与消息路由，而不是传统阻塞式业务请求处理。
- `M1` 的首要目标是协议正确性、边界清晰与可验证性。

Quarkus 官方文档说明：

- Quarkus 底层建立在 Vert.x 之上。
- Quarkus Messaging 的执行模型中，event-loop 是标准选择之一，并明确强调 event-loop 上不能执行阻塞操作。

## 决策

`M1` 阶段采用以下实现原则：

1. Broker 主链路采用响应式、非阻塞实现。
2. 主链路线程模型采用 Vert.x event loop。
3. `transport -> protocol -> session/routing` 的热路径不得执行阻塞操作。
4. 如后续引入阻塞型外部依赖，必须通过显式隔离或异步边界处理，不能直接进入 event loop 热路径。

## 这里的“响应式”具体指什么

本项目中的“响应式”不是指必须到处暴露复杂响应式类型，而是指：

- 以事件驱动方式处理连接、报文与状态流转。
- 主链路逻辑遵守非阻塞约束。
- 不以“一个连接占用一个阻塞线程”的命令式模型作为基础实现。

## 原因

- 这与 Quarkus + Vert.x 的基础架构一致。
- MQTT Broker 主链路天然适合事件驱动和状态机实现。
- 该模式更容易保持连接处理、报文处理与路由处理的一致性。
- 在 `M1` 阶段先保证 event loop 热路径纯净，比混入阻塞模型更稳妥。

## 结果

- `transport` 层以 Vert.x 事件回调驱动主链路。
- `protocol` 层接口设计应支持快速、短路径、无阻塞处理。
- `session` 与 `routing` 的内存实现优先保持 event-loop 友好。
- 后续测试与代码审查要把“是否阻塞 event loop”作为显式检查项。
- 在需要接入 Vert.x 扩展能力时，若存在 Mutiny 变体，默认优先使用 Mutiny 包装层，使主链路的响应式 API 风格保持一致。

## 实施约束

- 不在主链路中执行阻塞 I/O。
- 不在主链路中直接调用阻塞式数据库、文件或远程服务。
- 不在主链路中引入与执行模型不一致的隐式线程切换。
- 若必须跨线程处理，必须在文档和代码中显式标出边界。

## 不采纳方案

### 以阻塞式命令模型作为 `M1` 主链路基础

当前不采纳原因：

- 与 Vert.x MQTT server 的接入模型不一致。
- 会让热路径线程行为更难推断。
- 容易在早期把阻塞操作混入协议主链路。

## 参考依据

- Quarkus Reactive Architecture: https://quarkus.io/guides/quarkus-reactive-architecture
- Quarkus Vert.x Guide: https://quarkus.io/guides/vertx
- Quarkus Messaging Guide: https://quarkus.io/guides/messaging
