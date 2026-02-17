# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 語言

永遠使用繁體中文回覆。

## 專案目標

BtcTrade 是一個多用戶加密貨幣交易策略平台，核心功能：

1. **Google OAuth2 認證**：一鍵登入，自動建立用戶帳戶
2. **個人觀察清單**：從全域幣對池中選擇關注的交易對
3. **策略模板管理**：克隆系統預設策略 → 自訂參數 → 回測驗證
4. **綜合交易策略**：EMA/MACD 動量觸發 + ADX 趨勢過濾 + RSI 極端值保護 + 移動停利鎖定利潤
5. **回測引擎**：使用 Binance 歷史 K 線資料進行 walk-forward 模擬（純記憶體，零 DB 寫入）
6. **即時監控**：Binance WebSocket 串流 + 事件驅動策略評估
7. **多管道通知**：Discord Bot / Gmail SMTP / Telegram Bot 交易訊號通知

## 技術棧

- **框架**: Spring Boot 4.0.2 (Spring Framework 7.x, Jackson 3)
- **Java**: 21 (GraalVM CE)
- **構建工具**: Maven (透過 Maven Wrapper `./mvnw`)
- **資料庫**: PostgreSQL 17 (Docker 本地 / K8s 生產)
- **認證**: Spring Security + Google OAuth2
- **部署**: K8s (ArgoCD GitOps) + GitHub Actions CI/CD
- **映像倉庫**: ghcr.io/aiinpocket/crypto-monitor
- **前端**: Thymeleaf + Tailwind CSS (CDN) + Alpine.js + Chart.js
- **技術分析**: ta4j 0.17+
- **排程**: Quartz Scheduler (RAM JobStore)
- **即時通訊**: WebSocket
- **序列化**: Jackson 3 (`tools.jackson.databind.ObjectMapper`)

## Spring Boot 4 注意事項

- Jackson 3: 使用 `tools.jackson.databind.ObjectMapper`（非 `com.fasterxml.jackson`）
  - `writeValueAsString` 不拋 IOException，用 `Exception` catch
- Jakarta EE: `jakarta.*` 取代 `javax.*`
- ta4j 0.17: EMA 在 `org.ta4j.core.indicators.averages.EMAIndicator`，ADX 在 `org.ta4j.core.indicators.adx.ADXIndicator`，BarSeries 使用 `barBuilder()` 鏈式 API + `DecimalNumFactory`
- `RestClient.Builder` 不自動注入，使用 `RestClientConfig` 提供 Bean

## 常用命令

```bash
# ── 本地開發 ──
export GOOGLE_CLIENT_SECRET=your-secret
./mvnw spring-boot:run
./mvnw clean package
./mvnw test

# ── K8s 操作 ──
kubectl get pods -n app
kubectl logs -n app deployment/crypto-monitor-api -f
kubectl get application crypto-monitor -n argocd
kubectl rollout restart deployment/crypto-monitor-api -n app

# ── 觸發系統回測 ──
curl -X POST "http://localhost:8080/api/backtest/run?symbol=BTCUSDT&years=5"
```

## 架構

### 三層式 + 事件驅動

```
Controller 層
├── AuthController              → 登入頁面
├── DashboardController         → 主控台（注入用戶觀察清單）
├── StrategiesPageController    → 策略管理 + 回測頁面
├── SettingsController          → 通知設定頁面
├── WatchlistController         → REST: 觀察清單 CRUD
├── StrategyTemplateController  → REST: 策略模板 CRUD + 克隆
├── UserBacktestController      → REST: 用戶回測提交/查詢
├── NotificationController      → REST: 通知管道 CRUD + 測試
├── BacktestController          → REST: 系統回測
└── SymbolController            → REST: 全域幣對管理

Service 層
├── 交易引擎（無狀態）
│   ├── StrategyService            → 進出場規則判斷
│   ├── TechnicalIndicatorService  → ta4j 指標計算
│   ├── TradeExecutionService      → 策略評估 + 執行
│   ├── BacktestService            → Walk-forward 回測（支援自訂參數）
│   └── BarSeriesFactory           → K 線轉 BarSeries
├── 用戶服務
│   ├── UserWatchlistService       → 觀察清單 CRUD
│   ├── StrategyTemplateService    → 策略模板 CRUD + 系統預設初始化
│   ├── UserBacktestService        → @Async 用戶回測執行
│   └── NotificationChannelService → 通知管道 CRUD
├── 數據管線
│   ├── BinanceApiService          → Binance REST API 資料拉取
│   ├── BinanceWebSocketClient     → Binance WebSocket 串流
│   ├── BinanceStreamManager       → SmartLifecycle 串流管理
│   └── HistoricalSyncService      → @Async 歷史資料同步
├── 通知系統（Strategy Pattern）
│   ├── NotificationSender         → 介面（含 parseConfig 共用方法）
│   ├── DiscordNotificationSender  → Discord Bot API
│   ├── GmailNotificationSender    → SMTP 郵件
│   ├── TelegramNotificationSender → Telegram Bot API
│   └── NotificationDispatcher     → @Async 多用戶平行通知分發
└── 事件處理
    ├── KlineClosedEventHandler    → K 線收盤 → 策略評估
    └── KlineTickEventHandler      → K 線跳動 → 價格更新

Repository 層 → PostgreSQL
├── 市場數據: KlineRepository, TrackedSymbolRepository
├── 交易紀錄: TradePositionRepository, TradeSignalRepository
├── 用戶系統: AppUserRepository, UserWatchlistRepository
├── 策略回測: StrategyTemplateRepository, BacktestRunRepository
└── 通知管道: NotificationChannelRepository
```

### 多執行緒隔離（AsyncConfig）

| 執行緒池 | 線程前綴 | 核心/最大/佇列 | 用途 |
|----------|---------|----------------|------|
| historicalSyncExecutor | `hist-sync-` | 3 / 6 / 20 | 歷史資料同步（每幣對獨立） |
| notificationExecutor | `notify-` | 3 / 8 / 50 | 通知分發（Discord/Gmail/Telegram） |
| backtestExecutor | `backtest-` | 1 / 2 / 5 | 用戶回測計算（CPU 密集） |

### 共用元件

| 元件 | 提供方 | 說明 |
|------|--------|------|
| `ObjectMapper` | Spring Boot 自動配置 | Jackson 3，注入到所有需要的 Service |
| `RestClient` (restClient) | RestClientConfig | 通用 HTTP（Discord/Telegram 用） |
| `RestClient` (binanceRestClient) | RestClientConfig | Binance API 專用 |
| `IntervalParams` | IntervalConfig | 時間間隔參數 bean |
| `parseConfig()` | NotificationSender | 介面 default 方法，JSON 解析共用 |

## 策略邏輯

**進場**：(MACD 零軸交叉 OR EMA 交叉) AND EMA 趨勢確認 AND ADX > 20 AND RSI 在範圍內
**出場優先級**：
1. 日內停損/移動停利（使用 high/low 價格檢查，非收盤價）
2. RSI 極端值（>80 多頭出場，<20 空頭出場）
3. 時間止損（指定天數仍虧損則出場）
4. 最長持倉

**策略參數動態化**：`BacktestService.runBacktestWithParams()` 接受自訂 `TradingStrategyProperties`，
建立臨時的 `TechnicalIndicatorService` 和 `StrategyService` 實例（兩者皆無狀態）。

所有策略參數在 `application.yml` 的 `trading:` 區塊配置。

## 環境變數

| 變數 | 必填 | 說明 |
|------|------|------|
| `GOOGLE_CLIENT_SECRET` | **是** | Google OAuth2 Client Secret |
| `DB_USERNAME` | 否 | PostgreSQL 用戶名（預設 btctrade） |
| `DB_PASSWORD` | 否 | PostgreSQL 密碼（預設 btctrade_dev） |
| `SYSTEM_GMAIL_ADDRESS` | 否 | 系統通知用 Gmail 地址 |
| `SYSTEM_GMAIL_APP_PASSWORD` | 否 | Gmail App Password |

## 前端設計規範

- **框架**: Thymeleaf + Tailwind CSS (CDN) + Alpine.js
- **風格**: 暗色操盤手介面
- **配色**: bg-bg-dark (#0F172A), Gold primary (#F59E0B), Purple cta (#8B5CF6)
- **字體**: Orbitron（標題）/ Exo 2（內文）
- **互動**: cursor-pointer + transition-colors duration-200
- **圖表**: Chart.js 暗色主題

```
templates/
├── fragments/ (head.html, navbar.html, sidebar.html)
├── login.html, dashboard.html, strategies.html, backtest.html, settings.html
```

## K8s 部署架構

- **線上環境**: https://crypto-monitor.aiinpocket.com
- **命名空間**: `app`
- **CI/CD**: GitHub Actions → ghcr.io → ArgoCD auto-sync
- **K8s Profile**: `application-k8s.yml`（`SPRING_PROFILES_ACTIVE=k8s`）
- **Manifests**: `k8s/manifests/` 目錄（ArgoCD 監控）
- **儲存**: NFS StorageClass（nfs-client）
- **TLS**: Cloudflare origin cert

### K8s Secrets（不在 Git 中）

| Secret 名稱 | Key | 說明 |
|-------------|-----|------|
| `crypto-monitor-secrets` | `GOOGLE_CLIENT_SECRET` | OAuth 密鑰 |
| `crypto-monitor-secrets` | `DB_PASSWORD` | PostgreSQL 密碼 |
| `ghcr-secret` | docker-registry | ghcr.io 映像拉取憑證 |
| `cloudflare-origin-cert-aiinpocket` | tls.crt / tls.key | TLS 憑證 |
