# vxmq

`vxmq` 是一个使用 Java 实现的 MQTT Broker 项目，目标是在严格遵循 MQTT 协议的前提下，同时支持 MQTT 3.1.1 与 MQTT 5，并建立可持续演进的工程基础。

当前仓库已完成 `M1 最小闭环`，具备单机 Broker 的最小可运行主链路，并已建立文档、测试和决策记录基础。当前阶段与能力边界统一查看 [`docs/01-status/current-status.md`](docs/01-status/current-status.md)。

## 文档入口

项目文档统一放在 [`docs/`](docs/README.md)。

当前优先阅读：

1. [`docs/00-foundation/vision.md`](docs/00-foundation/vision.md)
2. [`docs/00-foundation/scope.md`](docs/00-foundation/scope.md)
3. [`docs/00-foundation/compatibility.md`](docs/00-foundation/compatibility.md)
4. [`docs/01-status/current-status.md`](docs/01-status/current-status.md)
5. [`docs/01-status/mqtt5-feature-matrix.md`](docs/01-status/mqtt5-feature-matrix.md)
6. [`docs/02-architecture/architecture-overview.md`](docs/02-architecture/architecture-overview.md)
7. [`docs/07-project/collaboration.md`](docs/07-project/collaboration.md)

## 开发运行

以开发模式启动：

```sh
./mvnw quarkus:dev
```

打包：

```sh
./mvnw package
```

构建原生可执行文件：

```sh
./mvnw package -Dnative
```

应用配置文件位于：

```text
src/main/resources/application.yml
```

## 当前阶段

项目当前处于：`M1 已完成，M2 尚未开始`

当前已完成能力与后续规划请直接查看：

- [`docs/01-status/current-status.md`](docs/01-status/current-status.md)
- [`docs/01-status/milestones.md`](docs/01-status/milestones.md)
- [`docs/01-status/mqtt5-feature-matrix.md`](docs/01-status/mqtt5-feature-matrix.md)

## 协作说明

本项目后续默认以中文沟通，重要协作约定见 [`docs/07-project/collaboration.md`](docs/07-project/collaboration.md)。
