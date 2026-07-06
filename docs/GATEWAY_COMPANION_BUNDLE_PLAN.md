# Gateway Companion Bundle 分发计划

> **状态**：2.1.1 整改已合入（2026-07-06）；见 `docs/BUNDLE_2.1.1_REMEDIATION.md`  
> **Release**：v2.1 · **Windows Gateway only** · OpenClaw **>=2026.6.9** · **bundle 不含地图 canvas**

## 背景

LittleHelper / LoongClaw 开源上架时，完整用户体验依赖 Gateway 侧配套资源：

- `client.id` 白名单（`openclaw-android`；日后 `loongclaw-desktop`）
- `littlehelper-modal` skill（MODAL wire 规范）
- `:18889` 上传/下载与 workspace 脚本（如 `generate_gallery.py`）
- （可选）地图 canvas 由管理员自行部署，**不**打入 bundle

App 行为由 APK 固定；新用户需在**自有 Gateway** 上一次性安装上述资源，才能达到当前真机验收效果。

## 原则

1. **仓库是 Release 唯一真相源**——bundle 从 git tag 打出，不从 Gateway 现场 zip。
2. **Gateway Agent 不生成安装包**——仅用于开发期写入/试跑 skill（如 `publish_littlehelper_gateway_skill.py`）。
3. **用户安装 = 文件复制 + config merge + doctor**——不依赖 Agent 对话。
4. **bundle 与 App 同版本发布**（例：`v2.0.0` APK + `loongclaw-gateway-bundle-2.0.0.zip`）。

## 分工

| 角色 | 职责 |
|------|------|
| **Gateway / 团队** | 从 `~/.openclaw/` 提取 skill、scripts、canvas → 供审核合入 repo |
| **Cursor Agent（Agent 模式）** | 审核对齐契约后，制作 `manifest` / `install` / `doctor` / 打包脚本与 zip |
| **用户 Gateway 主机** | 解压 → `install.sh` / `install.ps1` → `doctor` → 重启 Gateway → 安装 App |

Gateway 负责「挖出来给你审」；审完进 repo 之后，由 Cursor Agent 主导把总安装包做完整、可版本化、可发给用户。

## 包内容分层

| Profile | 内容 | 用途 |
|---------|------|------|
| **P0 minimal** | 白名单说明、`littlehelper-modal` | 能聊、能弹 MODAL 白板 |
| **P1 standard**（默认） | P0 + 参考 skill + `:18889` sidecar + workspace 脚本 | 对齐真机多模态验收（画廊下载等） |
| **P2 full**（可选） | P1 + fund 等未纳入 bundle 的展示 skill | 演示与重度用户 |

## 目标目录结构（冻结后落地）

```
gateway-bundle/                     # 或 Release 时从 repo 多路径组装
├── manifest.json                   # bundleVersion、compatibleApp、installSteps
├── README.md                       # 用户向：装 OpenClaw → install → doctor → 装 App
├── install.ps1 / install.sh
├── doctor.ps1 / doctor.sh
├── config/
│   ├── openclaw.merge.json         # 仅 merge skills.load.extraDirs（schema-safe）
│   ├── CLIENT_ID_SETUP.md          # patch_clientid（JS，install 自动执行）
│   └── clients.json                # 路由文档（不 merge 进 openclaw.json）
├── scripts/
│   ├── install_bundle.py
│   ├── merge_openclaw_config.py
│   └── patch_clientid.ps1          # Gateway 原版：Windows OpenClaw JS 白名单
├── skills/
│   └── littlehelper-modal/         # + gateway-export/skills/*（standard）
└── workspace/scripts/              # 来自 gateway-export/scripts/
    └── generate_gallery.py 等
```

（**不含** `canvas/map.littlehelper.html`；地图参考 `scripts/canvas_map_littlehelper.html`。）

Release 产物：`loongclaw-gateway-bundle-<version>.zip`，与 APK 同 GitHub Release tag。

## 执行阶段

### 阶段 A — 现在（App 收尾并行）

- [ ] 维护 P0/P1 资源清单（见下文）
- [ ] 契约变更时同步 `SKILL.md` 与 `OPENCLAW_GATEWAY_CONTRACT.md`
- [ ] 一次「假装新用户」手动试装（拷 skill + canvas + 白名单 + 18889），记录缺口
- [ ] **不**做分发级 install/CI/Release（避免契约未 freeze 反复打包）

### 阶段 B — App 功能冻结后

- [x] `skills.load.extraDirs` merge（schema-safe，已验证）
- [x] `build_gateway_bundle.py`、`install`、`doctor`
- [x] Gateway harvest → `gateway-export/`（见 `EXPORT_NOTES.md`）
- [x] skill 脚本强制 + markdown 约束（2026-07-05）
- [x] 干净机 / 真机验收（2026-07-04 + 新机 2026-07-05）

> 注：与「连接稳定性 Phase B（前台 Service）」无关，见 `CHANGELOG.md`。

### 阶段 C — 开源 / Release 前

- [ ] CI 或 tag 脚本打 zip，与 APK 同 Release
- [x] README Gateway 安装指引
- [ ] ~~client.profile config merge~~ — **不采用**；Agent 靠 label + AGENTS.md（Gateway 2026-07-04 确认）

## Gateway export 交接格式

团队从开发 Gateway 提取资源时，建议目录：

```
gateway-export/
├── skills/<name>/SKILL.md          # 及子目录 scripts/
├── workspace/scripts/*.py
├── canvas/<static>.html
└── EXPORT_NOTES.md                 # 相对上一版改动、真机测过的场景
```

**不包含**：

- 个人/无关 skill（如其他业务 skill）
- `MEMORY.md` 原文（有用规则应 merge 进 skill 或 routing 说明）
- `gallery_cache_*` 等运行时生成物
- 含密钥的完整 `openclaw.json`

## 审核要点（合入 repo / 打包前）

- `littlehelper-modal` 与 `OPENCLAW_GATEWAY_CONTRACT.md` 一致
- 仅 LoongClaw / LittleHelper 相关资源
- `generate_gallery.py` 等为真机验收版（如 thumb 18789 / download 18889 分轨）
- Canvas 只含静态模板，不含开发测试页（除非明确作为 demo）
- `manifest.json` 声明 `bundleVersion`、`compatibleApp`

## doctor 检查项（计划）

1. `skills.load.extraDirs` 含 `shared-skills`（install merge 结果）
2. `GET http://{host}:18789/` → Gateway 进程可达
3. `littlehelper-modal/SKILL.md` 存在且字节数 ≥ 预期
4. （可选）管理员自行部署地图 canvas 时：`GET .../__openclaw__/canvas/map.littlehelper.html` → 200
5. `GET ...:18889/health` → 200（standard；sidecar 需手动/计划任务保活）
6. PC 客户端（可选）：`patch_clientid.ps1` / `-WithPcPatch` → `LOONGCLAW_DESKTOP`（Android App 不需要）

## 与现有开发流程的关系

| 场景 | 做法 |
|------|------|
| 日常改契约 | 改 repo `SKILL.md` → `publish_littlehelper_gateway_skill.py` 推到 dev Gateway |
| freeze 前 | Gateway 试通版 sync 回 repo，避免 Gateway 与 GitHub 版本分叉 |
| 给用户 / Release | **只**从 repo 打 bundle，不从 Gateway 磁盘 zip |

**SKILL 单一来源（打 zip 时）：** `littlehelper-modal` → `scripts/skills/`；其余参考 skill → `gateway-export/skills/`；workspace 脚本 → `gateway-export/scripts/`。改 skill 后须 `python scripts/build_gateway_bundle.py`，勿只改 Gateway 机器上的副本。

`publish_littlehelper_gateway_skill.py` 是**开发者联调**路径；`install.sh` 是**最终用户**路径。二者输入应同为仓库权威文件。

## 相关文档

- [OPENCLAW_GATEWAY_CONTRACT.md](OPENCLAW_GATEWAY_CONTRACT.md)
- [openclaw_client_handshake_guide.md](openclaw_client_handshake_guide.md) §一（交付包构成草案）
- [scripts/skills/littlehelper-modal/SKILL.md](../scripts/skills/littlehelper-modal/SKILL.md)
