# ADR-0004 M1 阶段的传输栈选择

- 状态：已接受
- 日期：2026-03-14

## 背景

在进入 `M1` 代码骨架实现前，需要先确定 Broker 主链路的传输栈。

当前候选方案主要有两类：

1. 直接使用 Netty 的 MQTT codec，自行搭建连接管理、编解码与协议接入层。
2. 使用 Vert.x MQTT server，在其事件与连接模型之上实现 Broker 语义。

当前项目事实：

- 项目基础框架为 Quarkus。
- 当前依赖中已经引入 `quarkus-vertx`。
- `M1` 目标是尽快形成严格遵循 MQTT 协议的最小闭环，而不是过早优化到底层网络细节。

根据官方资料：

- Quarkus 明确说明其底层使用 Vert.x，并支持注入受管的 Vert.x 实例。
- Vert.x MQTT 官方文档明确提供 MQTT server，并说明它不是完整 Broker，但可以用于构建类似 Broker 的系统。
- Netty 官方提供 MQTT 编解码能力，但它更偏底层积木，需要项目自己承担更多协议接入层工作。

## 决策

`M1` 阶段选择：

1. 使用 Quarkus 受管的 Vert.x 实例。
2. 使用 Vert.x MQTT server 作为 Broker 主链路的传输接入层。
3. 不直接基于裸 Netty 搭建 `M1` 的 MQTT 接入层。

## 原因

- 与 Quarkus 当前技术栈一致，集成成本低。
- 比裸 Netty 更接近 Broker 接入所需抽象，能够更快落地 `M1`。
- 仍保留对 Broker 语义层的完全控制，不会把协议行为交给黑盒 Broker 组件。
- 降低 `M1` 阶段的样板代码与底层接入复杂度。

## 结果

- `transport` 模块以 Vert.x MQTT server 为入口。
- `connection` 生命周期围绕 Vert.x MQTT 连接对象建模，但需封装项目自己的 `ClientConnection` 抽象。
- `protocol/session/routing` 层不得直接依赖 Vert.x MQTT 细节。
- 后续如果出现明确的功能缺口或性能瓶颈，再评估是否下沉到 Netty。

## 不采纳方案

### 直接使用 Netty MQTT codec

当前不采纳原因：

- `M1` 阶段实现成本更高。
- 会把更多精力花在接入层搭建，而不是 Broker 语义正确性。
- 与 Quarkus 当前受管 Vert.x 运行时的贴合度更差。

## 参考依据

- Quarkus Vert.x Guide: https://quarkus.io/guides/vertx
- Quarkus Vert.x Reference Guide: https://quarkus.io/guides/vertx-reference
- Vert.x MQTT: https://vertx.io/docs/vertx-mqtt/java/
- Netty MQTT API: https://netty.io/4.1/api/io/netty/handler/codec/mqtt/package-summary.html
