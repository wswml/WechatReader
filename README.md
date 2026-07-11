# WechatReader

微信 + 支付宝双平台账单实时读取 Xposed 模块，每日自动导出钱迹兼容 CSV。

## 功能

- **微信**：实时捕获所有消息类型（文本、红包、转账、微信支付凭证），写入本地日志
- **支付宝**：每日凌晨 2 点通过 root 轮询 `messagebox.db`，追加支付记录
- **每日导出**：凌晨 2 点自动解析日志，生成 [钱迹](https://qianji.app/) 兼容 CSV（含分类、金额、商户、支付方式）
- **通知提醒**：导出完成后弹出系统通知，显示交易笔数和总额

## 原理

### 微信

微信使用 [WCDB](https://github.com/Tencent/wcdb) 操作 SQLite，Java 层走的是 `com.tencent.wcdb.database.SQLiteDatabase`（不是 Android 原生的 `android.database.sqlite.SQLiteDatabase`）。

模块通过 Xposed Hook WCDB 的 `insert/insertWithOnConflict/insertOrThrow/replace/replaceOrThrow/update/updateWithOnConflict/execSQL` 共 8 个方法，过滤 `message` 表的写入操作，提取 `type/content/talker/isSend` 等字段。

输出格式（管道分隔）：

```
W|收到|文本|talker_id|消息内容
W|收到|红包|sender_id|<msg>...</msg>
W|收到|转账|sender_id|<msg>...</msg>
W|收到|other:318767153||<msg>...微信支付凭证...</msg>
```

### 支付宝

支付宝使用自研 JNI 原生库 `libalipay_sqlite_bindings.so` 直调 SQLite C API，Java 层钩子无效，因此采用轮询方案：

1. 凌晨 2 点闹钟触发 `DailyExportReceiver`
2. 通过 `su` 复制 `/data/data/com.eg.android.AlipayGphone/databases/messagebox.db` 到 `/data/local/tmp/`
3. Java `SQLiteDatabase` 打开副本，查询 `service_message` 表
4. 解析 JSON `content` 字段，提取 `topSubContent` / `content`（金额）/ `assistMsg1`（付款方式）/ `sceneExt2.sceneName`（商户）
5. 追加到 `messages.log`

输出格式：

```
[MM-dd HH:mm:ss] A|支出|20.00|商户名|零钱
```

### 导出

`DailyExportReceiver` 解析 `messages.log`：

| 来源 | 触发条件 | 分类 |
|------|----------|------|
| 支付宝 `A\|` | `isPaymentMsg=true` | 从关键词推断 |
| 微信红包 | `type=436207665` + content 含 `¥金额` | 红包 |
| 微信转账 | `type=419430449` + content 含 `¥金额` | 转账 |
| 微信支付凭证 | `type=318767153` + content 含 `¥金额` | 从关键词推断 |

分类规则（基于 content 文本关键词）：

| 关键词 | 分类 |
|--------|------|
| 公交/地铁/打车/高铁/加油/停车... | 交通 |
| 餐厅/外卖/麦当劳/奶茶/超市... | 三餐 |
| 淘宝/京东/拼多多/购物... | 购物 |
| 房租/水电/燃气/话费... | 居家 |
| 电影/KTV/游戏... | 娱乐 |
| 医院/药店... | 医疗 |
| 支付宝原有 `guessCategory()` | 原逻辑 |

## 文件结构

```
WechatReader/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── xposed_init
│       └── java/com/nous/wechatreader/
│           ├── WechatReader.java          # Xposed 入口，SQLite Hook
│           ├── DailyExportReceiver.java   # 闹钟接收器，每日导出 + 支付宝轮询
│           └── MessageWriter.java         # 线程安全日志写入
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 输出路径

| 文件 | 路径 |
|------|------|
| 消息日志 | `/sdcard/Download/WechatReader/messages.log` |
| 钱迹 CSV | `/sdcard/Download/qianji_import.csv` |
| 支付宝轮询状态 | `/sdcard/Download/WechatReader/.alipay_last_id` |

## 环境要求

- Android 8.0+
- LSPosed / EdXposed（Zygisk 模式）
- **Root 权限**（支付宝轮询需要 `su`）
- 微信作用域：`com.tencent.mm`
- 支付宝作用域：`com.eg.android.AlipayGphone`

## 编译

```bash
# Android Studio → Build APK
# 或命令行
./gradlew assembleRelease
```

安装后在 LSPosed 管理器勾选作用域，重启微信和支付宝。

## 限制

- 支付宝非实时（每日凌晨 2 点轮询一次）
- 红包金额需拆开后才知道（通知消息不含 ¥）
- 分类基于关键词，偶尔可能不准
- 需要 root

## 待改进

- [ ] 支付宝：逆向 JNI 层 `libalipay_sqlite_bindings.so` 实现实时 Hook
- [ ] 微信支付凭证：更完整的 XML 解析（商品说明、交易单号）
- [ ] 分类：接入 AI 分类或自定义规则
- [ ] 数据去重：CSV 增量导入而非全量覆盖

## License

MIT
