# M3-10 客户端认证与鉴权架构设计

## 背景

`vxmq` 当前处于 M3 阶段，Broker 主链路已经具备 CONNECT、会话、订阅、发布、QoS 1、QoS 2、retained、will 和关键 MQTT 5 属性的基础能力。认证侧目前只有早期 `PermitAllAuthnProvider`，`DefaultProtocolEngine` 在 CONNECT 阶段调用它决定是否允许连接。鉴权侧尚未接入 SUBSCRIBE 或 PUBLISH 主链路。

M3-10 的路线图标题是“用户名密码鉴权（基础版）”，但本次设计目标不应停留在玩具级用户名密码判断。认证和鉴权在实际 Broker 中通常来自外部系统，未来也会由后台管理系统在运行时创建、启停、排序和修改。本次需要在当前配置驱动场景下落地最小认证实现，同时让架构能承接未来运行时用户操作驱动。

## 参考设计

本设计参考 EMQX 和 HiveMQ 的官方资料，但不照搬具体实现。

- EMQX 将认证拆成 `mechanism + backend + configuration`，支持多个 authenticator 组成有序链，资源可启停、重排，并按结果决定继续、允许或拒绝。
- EMQX 鉴权同样支持多个 authorizer 组成有序链，针对 publish/subscribe 顺序检查，并有 no-match 行为、deny 行为和缓存等全局设置。
- HiveMQ 的 Extension Security Registry 也把认证器、发布鉴权器、订阅鉴权器分开，认证成功不等于后续操作全部允许；发布和订阅可在每次操作上独立授权。

项目采纳的架构要点：

- 认证和鉴权分离。
- 认证链和鉴权链都以有序资源为核心。
- 当前配置文件是资源来源之一，未来后台管理系统可以成为另一个资源来源。
- 当前具体实现只做静态用户名密码认证；鉴权链接入主链路但默认放行。

参考链接：

- https://docs.emqx.com/en/emqx/latest/dashboard/authn.html
- https://docs.emqx.com/en/emqx/latest/dashboard/authz.html
- https://docs.emqx.com/en/emqx/latest/access-control/authn/authn.html
- https://docs.emqx.com/en/emqx/latest/access-control/authz/authz.html
- https://docs.hivemq.com/hivemq/latest/extensions/authentication.html
- https://docs.hivemq.com/hivemq/4.5/extensions/authorization.html

## 范围

本次实现范围：

- 实现客户端认证链，并提供配置驱动的静态用户名密码 authenticator。
- 保留默认 permit-all 行为，避免未配置认证时阻断开发和测试主链路。
- 将鉴权链接入 `SUBSCRIBE` 和 `PUBLISH` 主链路。
- 当前鉴权实现只提供默认 permit-all authorizer，不提供实际 ACL 规则。
- 补齐 CONNECT password 值在 transport 到 protocol model 的传递，因为现有 `ConnectRequest` 只有 `passwordPresent`，不足以做用户名密码认证。
- 更新稳定文档和特性矩阵，明确当前能力边界。

本次不实现：

- HTTP、MySQL、PostgreSQL、Redis、LDAP、JWT、SCRAM、X.509 或 TLS。
- ACL 文件、角色、权限模板或 topic 变量替换。
- 后台管理 API、管理 UI 或运行时 CRUD。
- 跨节点同步、持久化存储或配置热重载。
- 认证或鉴权缓存。
- 密码哈希格式。M3-10 先用明文配置完成架构闭环，后续可在同一 static backend 增加 hash scheme。

## 架构

认证模块从当前 `AuthnProvider` 演进为认证链模型。为了减少现有调用面的冲击，可以保留 `AuthnProvider` 作为 `DefaultProtocolEngine` 的入口名，但其内部职责升级为“认证协调器”。更清晰的长期命名是 `AuthnAuthenticator` 与 `AuthnChain`。

建议结构：

```text
io.github.vxmqmqtt.vxmq.authn
  AuthnProvider
  AuthnContext
  AuthnResult
  AuthnResultStatus
  AuthnReason
  AuthnAuthenticator
  AuthnChain
  PermitAllAuthnAuthenticator
  StaticPasswordAuthnAuthenticator
  ConfiguredAuthnProvider

io.github.vxmqmqtt.vxmq.authz
  AuthzProvider
  AuthzContext
  AuthzResult
  AuthzResultStatus
  AuthzReason
  AuthzAction
  AuthzAuthorizer
  AuthzChain
  ConfiguredAuthzProvider
  PermitAllAuthzAuthorizer
```

`ConfiguredAuthnProvider` 是当前 CDI 注入给 `DefaultProtocolEngine` 的实现，负责根据配置构造认证链。未来后台管理系统不应直接改 protocol，而是替换或扩展资源提供者，例如从配置、数据库或运行时内存仓库生成同样的 authenticator definitions。

鉴权模块独立成 `authz` 包，避免把连接认证和发布订阅授权混在一起。`DefaultProtocolEngine` 只依赖 `AuthzProvider`，provider 内部协调 `AuthzChain` 和具体 `AuthzAuthorizer`。`handleSubscribe` 对每个 topic filter 调用订阅鉴权，`handlePublish` 对 topic name 调用发布鉴权。

## 决策模型

认证决策：

- `ALLOW`：认证成功，连接继续。
- `DENY`：认证失败，连接拒绝。
- `ABSTAIN`：当前 authenticator 不适用或未找到凭据，认证链继续检查下一个 authenticator。

认证链行为：

- disabled authenticator 跳过。
- authenticator 返回 `ALLOW` 时立即允许连接。
- authenticator 返回 `DENY` 时立即拒绝连接。
- authenticator 返回 `ABSTAIN` 时继续下一个。
- 所有 authenticator 都未匹配时，按全局 `no-match` 策略处理。Broker 默认配置是 `allow`，用于保持未配置资源时的 permit-all 行为；一旦启用了至少一个认证资源但省略 `no-match`，构建逻辑按 `deny` fail-closed，避免配置了用户却放行未知客户端。

鉴权决策：

- `ALLOW`：允许当前操作。
- `DENY`：拒绝当前操作。
- `ABSTAIN`：当前 authorizer 没有命中规则，鉴权链继续。

鉴权链行为：

- disabled authorizer 跳过。
- `ALLOW` 或 `DENY` 都是最终决策。
- 所有 authorizer 都未匹配时，按全局 `no-match` 策略处理。Broker 默认配置是 `allow`，用于保持当前 publish/subscribe permit-all 行为；后续启用具体 authorizer 资源但省略 `no-match` 时，构建逻辑按 `deny` fail-closed。

当前 M3-10 只会存在 permit-all authorizer，因此鉴权不会改变现有发布订阅行为。

## 配置模型

当前配置驱动采用 `application.yml` 下的 broker 配置扩展：

```yaml
vxmq:
  broker:
    authn:
      no-match: deny
      authenticators:
        - id: local-users
          enabled: true
          mechanism: password
          backend: static
          users:
            - username: device-a
              password: secret-a
            - username: device-b
              password: secret-b
    authz:
      no-match: allow
```

字段含义：

- `id`：资源稳定标识，未来 UI 和 API 使用同一标识。
- `enabled`：资源启停，不删除配置。
- `mechanism`：认证机制，例如当前的 `password`。
- `backend`：凭据或规则来源，例如当前的 `static`。
- `users`：当前 static backend 的最小凭据集合。
- `no-match`：链无最终决策时的默认行为，取值 `allow` 或 `deny`。

未配置任何 enabled authenticator/authorizer 时，Broker 使用 permit-all 链。配置了至少一个 enabled 资源时，建议显式设置 `no-match: deny`；若用户省略该字段，构建逻辑也按 deny 处理。

实现时可以用 SmallRye Config Mapping 建模。若 Quarkus 对 list mapping 的 Optional 支持复杂，允许用空列表默认值和单独的 test config 覆盖，但对外 YAML 形态保持稳定。

## 数据流

CONNECT：

1. `VertxMqttBrokerTransport` 从 `MqttAuth` 读取 username 和 password。
2. `ConnectRequest` 保存 username、password、passwordPresent、MQTT 5 CONNECT user properties。
3. `DefaultProtocolEngine.handleConnect` 在协议版本校验后调用 `AuthnProvider.authenticate`。
4. `ConfiguredAuthnProvider` 构建 `AuthnContext` 并执行认证链，返回 `AuthnResult`。
5. 拒绝时返回当前已有 not-authorized CONNACK 语义；允许时继续 clientId 解析。
6. 若 CONNECT 携带 Will Message，protocol 在 session mutation 前通过 `AuthzProvider` 检查一次 publish 权限；默认 permit-all 不改变当前行为。未来接入实际 ACL 后，Will 无发布权限时拒绝 CONNECT。
7. 认证和 Will 鉴权都通过后，继续 session 和 takeover 流程。

SUBSCRIBE：

1. `DefaultProtocolEngine.handleSubscribe` 先校验 topic filter 和 QoS。
2. 对每个合法 `SubscriptionItem` 调用 `AuthzProvider.authorize`。
3. 允许时保留现有订阅注册、retained replay 行为。
4. 拒绝时不修改 session 或 routing，MQTT 5 返回 `NOT_AUTHORIZED`，MQTT 3.1.1 返回 failure 等价结果。

PUBLISH：

1. `DefaultProtocolEngine.handlePublish` 先校验 topic name 和 QoS。
2. 调用 `AuthzProvider.authorize`。
3. 允许时保留现有 retained 更新、QoS 2 deferred routing 和投递行为。
4. 拒绝时不更新 retained，不路由，不创建 QoS 2 入站状态。MQTT 5 使用 not authorized 类 reason code；MQTT 3.1.1 关闭连接。

当前鉴权默认放行，因此新增接入点应有测试证明它不改变默认行为，同时也要有拒绝路径单测，保证未来 ACL authorizer 接入时主链路已经可靠。

## 错误处理与日志

- 认证拒绝记录 protocol warning，避免输出明文密码。
- 配置中出现未知 mechanism/backend 时应在启动时失败，而不是运行时静默放行。
- 重复用户名在同一个 static authenticator 中应启动失败，避免配置歧义。
- 空用户名只在配置中显式存在空用户名时可匹配；默认不把空用户名当成匿名用户。
- password 不应进入日志、异常消息或 `toString` 输出。

## 测试策略

单元测试：

- `StaticPasswordAuthnTest`
  - 正确用户名密码返回 `ALLOW`。
  - 用户不存在返回 `ABSTAIN`。
  - 用户存在但密码错误返回 `DENY`。
  - password 未提供时按错误密码处理。
- `AuthnChainTest`
  - 顺序执行，`ALLOW` 和 `DENY` 都短路。
  - 所有项 `ABSTAIN` 时使用 no-match。
  - disabled authenticator 被跳过。
- `AuthzChainTest`
  - publish/subscribe 默认 permit-all。
  - 拒绝路径返回 `DENY` 并被 protocol 正确映射。
- `DefaultProtocolEngineTest`
  - CONNECT 被认证拒绝时不创建 session、不绑定 clientId。
  - SUBSCRIBE 被鉴权拒绝时不修改 session/routing。
  - PUBLISH 被鉴权拒绝时不路由、不更新 retained。

传输层测试：

- `VertxMqttBrokerTransportTest`
  - CONNECT password 从 Vert.x endpoint 映射进 `ConnectRequest`。

集成测试：

- 配置一个 static 用户，使用正确密码能连接。
- 使用错误密码时连接被拒绝。

## 文档更新

实现完成后更新：

- `docs/02-system/system-design.md`：扩展 authn/authz 模块职责。
- `docs/02-system/connection-lifecycle.md`：把默认放行改为配置驱动认证链。
- `docs/02-system/message-delivery.md`：说明 PUBLISH/SUBSCRIBE 鉴权接入点和当前 permit-all 边界。
- `docs/01-status/current-status.md`、`docs/01-status/roadmap.md`、`docs/01-status/mqtt5-feature-matrix.md`：标记 M3-10 完成状态和限制。

## 未来扩展路径

后台管理系统出现后，应复用同一资源模型：

- 管理 UI 创建、编辑、启停、排序 authenticator/authorizer。
- 管理 API 写入运行时资源仓库。
- `AuthnChain` 和 `AuthzChain` 从仓库快照读取资源，不依赖配置文件来源；协议层只依赖 `AuthnProvider` / `AuthzProvider`。
- 配置文件仍可作为启动时 seed resource。

后续可逐步加入：

- static backend 密码 hash scheme。
- file ACL authorizer。
- HTTP authenticator/authorizer。
- SQL/Redis backend。
- 认证和鉴权缓存。
- 资源状态、健康、指标和审计日志。
- MQTT 5 Enhanced Authn。
