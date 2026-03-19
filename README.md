# vxmq

`vxmq` 是一个使用 Java 实现的 MQTT Broker 项目，目标是在严格遵循 MQTT 协议的前提下，同时支持 MQTT 3.1.1 与 MQTT 5，并建立可持续演进的工程基础。

当前仓库处于项目基础设施建设阶段，优先完成以下事项：

- 建立统一文档体系。
- 明确项目愿景、范围和协作规范。
- 形成 MQTT 5 特性矩阵与架构骨架。
- 在此基础上逐步推进实现、测试和验证。

## 文档入口

项目文档统一放在 [`docs/`](docs/README.md)。

当前优先阅读：

1. [`docs/00-overview/vision.md`](docs/00-overview/vision.md)
2. [`docs/00-overview/scope.md`](docs/00-overview/scope.md)
3. [`docs/01-requirements/mqtt5-feature-matrix.md`](docs/01-requirements/mqtt5-feature-matrix.md)
4. [`docs/01-requirements/compatibility.md`](docs/01-requirements/compatibility.md)
5. [`docs/02-architecture/architecture-overview.md`](docs/02-architecture/architecture-overview.md)
6. [`docs/07-project/collaboration.md`](docs/07-project/collaboration.md)
7. [`docs/07-project/milestones.md`](docs/07-project/milestones.md)

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

## 当前阶段目标

第一阶段聚焦单机 Broker 核心能力：

- 连接与会话管理。
- 发布订阅主链路。
- QoS 0 / 1 / 2 基础语义。
- 保留消息与遗嘱消息。
- MQTT 5 核心属性支持。
- 基础观测与测试基础设施。

## 协作说明

本项目后续默认以中文沟通，重要协作约定见 [`docs/07-project/collaboration.md`](docs/07-project/collaboration.md)。
