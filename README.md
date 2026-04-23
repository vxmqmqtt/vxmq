# vxmq

`vxmq` 是一个使用 Java 实现的 MQTT Broker 项目，目标是在严格遵循 MQTT 协议的前提下，同时支持 MQTT 3.1.1 与 MQTT 5，并建立可持续演进的工程基础。

## 快速入口

- 文档导航：[`docs/README.md`](docs/README.md)
- 当前状态：[`docs/01-status/current-status.md`](docs/01-status/current-status.md)
- 代码入口：[`src/main/java/io/github/vxmqmqtt/vxmq`](src/main/java/io/github/vxmqmqtt/vxmq)
- 运行配置：[`src/main/resources/application.yml`](src/main/resources/application.yml)

## 开发运行

开发模式：

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

## 协作说明

项目默认以中文沟通，协作约定见 [`docs/07-project/collaboration.md`](docs/07-project/collaboration.md)。
