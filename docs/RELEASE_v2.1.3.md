# LoongClaw v2.1.3 — GitHub Release 说明

> 维护者备忘见文末「§ 发版前自检」。下面 **「粘贴到 GitHub」** 一节可直接复制到 Release → Description。

---

## 截图要不要放？

**不强制，但建议放 1～2 张。**

| 做法 | 说明 |
|------|------|
| **推荐** | 真机截屏：主聊天 + 白板，或 **设置 → 测试握手** 四步进度。在 GitHub 创建 Release 时 **把图片拖进 Description 编辑框**，会自动上传并生成链接。 |
| **暂无真机图** | 可先用仓库里的设计参考图（见下方可选块），或首版只发文字 + APK，后续 Release 再补图。 |
| **不必** | 不要把截图打进 APK 附件区；Release 正文里嵌入即可。 |

首版开源：**有图更吸引人，没有也不影响安装**。README 里已有 `docs/UIReferenceDesign.png`，Release 可与 README 共用同一张，避免重复维护多张。

---

## 粘贴到 GitHub（Release `v2.1.3`）

**仓库：** [github.com/laomianhua/LoongClaw-Android](https://github.com/laomianhua/LoongClaw-Android)（首次 push 后生效）

```markdown
## LoongClaw for Android v2.1.3（龙爪）

龙爪是面向自托管 **[OpenClaw](https://github.com/openclaw/openclaw) Gateway** 的 Android 客户端：手机侧聊天、MODAL 白板、附件上传、文件下载与 TTS；语义与多模态由 Gateway 上的 Agent 完成。

### 下载

| 文件 | 说明 |
|------|------|
| **APK**（本页附件） | 安装到手机。内测可用 `app-debug.apk`；对外建议自行签名后的 release 包 |
| **loongclaw-gateway-bundle-2.1.3.zip** | 在 **Windows Gateway 主机**解压，运行 `install.ps1`（详见 zip 内 README） |

需要：**OpenClaw ≥ 2026.6.9**、Tailscale 或局域网 `ws://`（暂不支持公网 `wss://`）。

### 快速开始

**手机（龙爪 App）**

1. 安装 APK → 首次启动进入 **设置**
2. 填写 Gateway 地址、端口 `18789`、Token → **测试握手** → **保存并连接**
3. 若提示配对：Control UI → Devices → 批准本设备

**Gateway 主机（Windows）**

1. 解压 `loongclaw-gateway-bundle-2.1.3.zip`
2. `powershell -ExecutionPolicy Bypass -File .\install.ps1`
3. 重启 Gateway；另开终端：`python %USERPROFILE%\.openclaw\companion\upload_server.py`
4. `.\doctor.ps1` 全部 `[OK]` 后再用手机连接

完整文档：[README（中文）](https://github.com/laomianhua/LoongClaw-Android/blob/master/README.md) · [README.en.md](https://github.com/laomianhua/LoongClaw-Android/blob/master/README.en.md)

### 本版本亮点

- 连接 OpenClaw Gateway（device-auth v3、四步握手、前台保活）
- MODAL 白板：表格、Markdown、WebView（画廊 / PDF / 文件管理等依赖 bundle `standard`）
- 修复聊天用户气泡偶发丢失
- 文件管理器 / 画廊：**删除**与列表刷新（需新 APK + bundle 2.1.3）
- 设置页 **关于**：版本号 **2.1.3**、开发者联系邮箱
- Gateway Companion Bundle **2.1.3**（含 `pdf.js`、`save_to_storage`、sidecar 删除与 CORS）

### 已知限制

- 设置内扫码为占位；请手动填 Token + Control UI 配对
- 界面语言：设置 / 上传 / 我的文件支持 English；主聊天与连接横幅可能仍为中文
- Companion bundle 目标平台：**Windows Gateway**（v2.1.x）
- 地图 canvas 不含在 bundle 内，需 Gateway 管理员自行部署

### 反馈

**laomianhua@agent.qq.com**

完整变更：[CHANGELOG.md](https://github.com/laomianhua/LoongClaw-Android/blob/master/CHANGELOG.md)
```

---

## 发版前自检（维护者，勿贴进 Release）

- [ ] `python scripts/build_gateway_bundle.py` → `dist/loongclaw-gateway-bundle-2.1.3.zip`
- [ ] `gradlew assembleDebug` 或签名后的 release APK
- [ ] 对照 [bundle 回归清单](BUNDLE_2.1.1_REMEDIATION.md#六下一版-bundle-回归清单15-分钟)（PDF、文件删除、`save_to_storage` → 画廊）
- [ ] [2.1.3 回归要点](SYSTEM_CANVAS_PLAN.md#213-回归要点发布前)（删除后列表刷新需新 HTML）
- [ ] Release 附件：APK + bundle zip；Description 粘贴上一节正文
