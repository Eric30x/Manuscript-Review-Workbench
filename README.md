# AI 新闻稿审稿工作台

用一篇校园新闻稿演示 Word 投稿、人数核验、三级审核、退回重提和签发下载。安卓端保留内置样例，也可以连接团队后端跑完整流程。

## Windows 启动后端

在仓库根目录打开 PowerShell。

```powershell
python -m pip install -r backend/requirements.txt
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
notepad backend/.env
```

在 `backend/.env` 填好自己的 `DEEPSEEK_API_KEY`，并设置 `WORKBENCH_DEMO_PASSWORD` 作为四个演示角色的登录口令。保存后运行：

```powershell
python backend/samples/make_sample_docs.py
python -m uvicorn backend.server.main:app --host 0.0.0.0 --port 8000
```

保持终端开着，在电脑浏览器打开 [健康检查](http://127.0.0.1:8000/health)。看到 `status` 为 `ok`，服务就已启动。`ai_ready` 为 `true` 表示后端已读取密钥。

默认用 `backend/data` 保存稿件、版本、退改意见和审核记录，重启可以继续使用。要使用 MySQL，先创建 `workbench` 数据库，设置 `WORKBENCH_MYSQL_URL`，再启动同一个后端进程。示例见 `backend/.env.example`。MySQL 保存元数据，Word 和每版检查结果仍保存在数据目录。开始正式联调时先选定存储方式，并保持数据库和数据目录的配置一致。

## 安卓真机

1. 在 Android Studio 打开仓库里的 `android` 目录，构建并运行 Debug 版。也可以直接安装与此版本对应的 Debug APK。
2. 电脑和手机连到同一个局域网。在电脑运行 `ipconfig`，找到当前网卡的 IPv4 地址。如果手机访问不了，在浏览器先试 `http://电脑IP:8000/health`，并检查 Windows 防火墙是否允许 Python 在本地专用网络接收入站连接。用 Android Studio 模拟器时，地址用 `http://10.0.2.2:8000`。
3. 把 `backend/samples/sample-docs/` 中四份 Word 放进手机的「下载」。在 App 角色选择页打开「连接团队后端」，填 `http://电脑IP:8000` 和自己设置的演示口令。这样后续操作都使用这个后端。每次切换角色，重新输入同一个演示口令。

## 真机验收顺序

| 角色 | 操作 | 应看到的结果 |
|---|---|---|
| 写稿人 | 上传「稿件初稿.docx」，再选择「报名统计.docx」「签到汇总.docx」两份参考材料 | 「我的稿件」里出现草稿 |
| 写稿人 | 打开草稿，点右上角「AI 检查」→「一键 AI 检查」 | 核验指出「约200人到场」缺乏依据。展开材料能看到 200 人报名和 126 人签到，点「跳到原句」可定位 |
| 写稿人 → 编辑初审 | 作者回到稿件详情送审。切换为编辑，在「待我处理」打开该稿并通过 | 稿件进入指导老师二审 |
| 指导老师 | 在「待我处理」打开该稿，针对原句填写意见并「确认退回」 | 稿件变为「已退回」，意见记录原句和稿件版本 |
| 写稿人 | 在「已退回」点意见里的「定位原文」，再点「上传新版 Word」选择「稿件新版.docx」。运行核验并重新送审 | 第 2 版沿用原来的材料，核验页面可见上轮意见，新稿回到指导老师二审 |
| 指导老师 → 负责人 | 指导老师查第 1 版意见和第 2 版结果，确认无误后通过。切换负责人，确认签发 | 「已签发」可导出锁定的 Word，文末有三审署名 |
| 写稿人 | 退出再进 App，登录后打开稿件 | 能查到签发状态、两版稿件、意见和流转记录，并重新下载签发版 |

## 开发验证

```powershell
python -m pytest -q backend/tests
```

默认用模拟 AI 结果验证服务端流程。要单独跑真实 DeepSeek 测试，请在运行测试的 PowerShell 设置 `RUN_LIVE_AI=1`，并将密钥设为该终端的 `DEEPSEEK_API_KEY` 环境变量。
