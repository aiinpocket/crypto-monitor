# BtcTrade - Multi-User Crypto Trading Strategy Platform

Spring Boot 4.0.2 + Java 21 多用戶加密貨幣交易策略平台，支援歷史回測、即時監控、
多管道通知（Discord / Gmail / Telegram）和自訂策略模板。
使用 Binance 公開 API 取得市場數據。

## 功能總覽

| 模組 | 說明 |
|------|------|
| Google OAuth2 登入 | 一鍵登入，自動建立用戶帳戶 |
| 個人觀察清單 | 從全域幣對池中選擇想關注的交易對 |
| 策略模板管理 | 克隆系統預設策略 → 自訂參數 → 回測驗證 |
| 即時 WebSocket | 價格跳動、交易訊號、同步進度即時推送 |
| 多管道通知 | Discord Bot / Gmail SMTP / Telegram Bot 交易訊號通知 |
| 非同步回測 | 獨立執行緒池背景運算，不影響即時交易 |
| 暗色操盤介面 | Tailwind CSS + Alpine.js，操盤手風格 UI |

## 策略概述

**核心邏輯：EMA/MACD 動量觸發 + ADX 趨勢過濾 + 移動停利鎖定利潤**

### 進場訊號
- **觸發**：MACD 零軸交叉（主要）或 EMA(12/26) 交叉（次要）
- **趨勢確認**：EMA short >= EMA long（多頭）或反之
- **過濾**：ADX > 20（趨勢市場）、RSI 在進場範圍內
- 支援 LONG 和 SHORT 雙向

### 出場條件（優先順序）
1. **停損**：最大單筆虧損限制（日內用 high/low 價格檢查）
2. **移動停利**：獲利達門檻後啟動，鎖定峰值利潤
3. **時間止損**：持倉超過指定天數仍虧損則出場
4. **RSI 極端值**：RSI > 80（多頭出場）或 RSI < 20（空頭出場）
5. **最長持倉**：強制平倉

### 日線回測成績（5 年，2021-02 ~ 2026-02）

| 指標 | 數值 |
|------|------|
| 年化報酬 | **30.17%** |
| 最大回撤 | -27.1% |
| Sharpe Ratio | 1.09 |
| Profit Factor | 2.16 |
| 勝率 | 44.0% (50 筆) |
| 初始 → 最終 | $10,000 → $37,369 |

## 技術棧

| 項目 | 技術 |
|------|------|
| 語言 | Java 21 |
| 框架 | Spring Boot 4.0.2, Spring Framework 7.x |
| 認證 | Spring Security + Google OAuth2 |
| 資料庫 | PostgreSQL 17 |
| 技術分析 | ta4j 0.17+ |
| 排程 | Quartz Scheduler (RAM JobStore) |
| 即時通訊 | WebSocket (`/ws/trades`) |
| 前端 | Thymeleaf + Tailwind CSS (CDN) + Alpine.js |
| 序列化 | Jackson 3 (`tools.jackson`) |
| 圖表 | Chart.js (CDN) |
| 容器 | Docker Compose (開發) / Kubernetes (生產) |
| CI/CD | GitHub Actions + ArgoCD (GitOps) |
| 容器映像 | ghcr.io (GitHub Container Registry) |

## 架構

### 三層式架構 + 事件驅動

```
┌─────────────────────────────────────────────────────┐
│ 前端 (Thymeleaf + Tailwind + Alpine.js)             │
│ ┌──────────┐┌──────────┐┌──────────┐┌──────────┐   │
│ │Dashboard ││Strategies││ Backtest ││ Settings │   │
│ └──────────┘└──────────┘└──────────┘└──────────┘   │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────┴──────────────────────────────────┐
│ Controller 層                                        │
│ Dashboard / Strategies / Backtest / Settings         │
│ WatchlistAPI / StrategyAPI / NotificationAPI          │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────┴──────────────────────────────────┐
│ Service 層                                           │
│ ┌─────────────────┐  ┌──────────────────┐           │
│ │ 交易引擎         │  │ 用戶服務          │           │
│ │ StrategyService  │  │ UserWatchlist     │           │
│ │ TechnicalIndicator│ │ StrategyTemplate  │           │
│ │ TradeExecution   │  │ UserBacktest      │           │
│ │ BacktestService  │  │ NotificationChannel│           │
│ └─────────────────┘  └──────────────────┘           │
│ ┌─────────────────┐  ┌──────────────────┐           │
│ │ 數據管線         │  │ 通知系統          │           │
│ │ BinanceApiService│  │ NotificationDispatcher│       │
│ │ HistoricalSync   │  │ Discord / Gmail /  │         │
│ │ BinanceWebSocket │  │ Telegram Sender    │         │
│ └─────────────────┘  └──────────────────┘           │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────┴──────────────────────────────────┐
│ Repository 層 → PostgreSQL                           │
│ Kline / TradePosition / TradeSignal / TrackedSymbol  │
│ AppUser / UserWatchlist / NotificationChannel         │
│ StrategyTemplate / BacktestRun                       │
└─────────────────────────────────────────────────────┘
```

### 安全機制

- **OAuth2 認證**：所有 API 端點強制 Google OAuth2 登入
- **資源授權檢查**：通知管道、策略模板等操作均驗證用戶所有權
- **輸入驗證**：回測參數範圍限制（年數 1~10、symbol 長度限制）
- **錯誤訊息保護**：API 僅回傳使用者友善錯誤，不暴露內部實作細節
- **XSS 防護**：前端使用 `x-text`（非 `x-html`）防止注入攻擊
- **WebSocket 死連線清理**：每 5 分鐘自動清理已關閉的 WebSocket session

### 響應式設計 (RWD)

- **手機版漢堡選單**：導航列自動收合為下拉選單
- **側邊欄適配**：手機版隱藏固定側邊欄，避免內容重疊
- **表格水平滾動**：交易紀錄、績效對比表支援手機端橫向捲動
- **Toast 通知系統**：取代 `alert()`，使用非阻塞式浮動通知

### 多執行緒隔離設計

| 執行緒池 | 用途 | 核心/最大/佇列 |
|----------|------|----------------|
| `hist-sync-*` | 歷史資料同步（每幣對獨立） | 3 / 6 / 20 |
| `notify-*` | 通知分發（Discord/Gmail/Telegram） | 3 / 8 / 50 |
| `backtest-*` | 用戶回測計算（CPU 密集） | 2 / 4 / 20 |

### 事件驅動流程

```
Binance WebSocket → KlineTick 事件 → 儲存 K 線
    └→ KlineClosed 事件 → 策略評估 → 交易訊號
        ├→ WebSocket 廣播（即時推送到前端）
        └→ NotificationDispatcher（@Async 多管道通知）
```

## 快速開始

### 前置需求
- Java 21+
- Docker & Docker Compose
- Maven 3.9+（或使用 `./mvnw`）
- Google OAuth2 Client（[Google Cloud Console](https://console.cloud.google.com/)）

### 環境變數

| 變數 | 必填 | 說明 |
|------|------|------|
| `GOOGLE_CLIENT_SECRET` | **是** | Google OAuth2 Client Secret |
| `DB_USERNAME` | 否 | PostgreSQL 用戶名（預設：btctrade） |
| `DB_PASSWORD` | 否 | PostgreSQL 密碼 |
| `SYSTEM_GMAIL_ADDRESS` | 否 | 系統通知用 Gmail 地址 |
| `SYSTEM_GMAIL_APP_PASSWORD` | 否 | Gmail App Password |

### 啟動

```bash
# 1. 設定環境變數
export GOOGLE_CLIENT_SECRET=your-google-oauth-secret

# 2. 啟動（Docker PostgreSQL + Spring Boot 一起啟動）
./mvnw spring-boot:run

# 3. 開啟瀏覽器
open http://localhost:8080
```

Google Console 設定 Redirect URI：`http://localhost:8080/login/oauth2/code/google`

### API 端點

#### 頁面路由

| Method | Path | 說明 |
|--------|------|------|
| GET | `/login` | Google 登入頁面 |
| GET | `/` | Dashboard（即時監控） |
| GET | `/strategies` | 策略模板管理 |
| GET | `/backtest` | 回測執行與結果 |
| GET | `/settings` | 通知管道設定 |

#### REST API

| Method | Path | 說明 |
|--------|------|------|
| GET/POST/DELETE | `/api/user/watchlist` | 觀察清單 CRUD |
| GET | `/api/user/strategies` | 列出策略模板 |
| POST | `/api/user/strategies/clone` | 克隆策略模板 |
| PUT/DELETE | `/api/user/strategies/{id}` | 更新/刪除模板 |
| POST | `/api/user/backtest/run` | 提交回測 |
| GET | `/api/user/backtest/history` | 回測歷史 |
| GET | `/api/user/backtest/{id}` | 回測結果 |
| GET/POST/DELETE | `/api/user/notifications` | 通知管道 CRUD |
| POST | `/api/user/notifications/{id}/test` | 測試通知連線 |

#### 系統端點

| Method | Path | 說明 |
|--------|------|------|
| POST | `/api/backtest/run` | 系統回測 |
| GET/POST/DELETE | `/api/symbols` | 全域幣對管理 |
| WS | `/ws/trades` | WebSocket 即時推送 |
| GET | `/actuator/health` | 健康檢查 |

## 策略參數

所有交易參數在 `src/main/resources/application.yml` 的 `trading:` 區塊：

```yaml
trading:
  interval: 5m              # 交易時間間隔（5m / 15m / 1h / 4h / 1d）
  strategy:
    ema-short: 12            # EMA 短期週期
    ema-long: 26             # EMA 長期週期
    rsi-period: 14           # RSI 週期
    macd-short: 12           # MACD 快線
    macd-long: 26            # MACD 慢線
    macd-signal: 9           # MACD 信號線
  risk:
    stop-loss-pct: 0.02      # 停損百分比
    trailing-activate-pct: 0.015  # 移動停利啟動
    trailing-offset-pct: 0.003   # 移動停利偏移
    time-stop-days: 1        # 時間止損天數
    max-holding-days: 3      # 最大持倉天數
    initial-capital: 10000   # 初始資金
```

## 排程任務

| 任務 | 排程 | 說明 |
|------|------|------|
| DataFetchJob | 每 5 分鐘 | Gap-fill 缺失的 K 線資料 |
| TradingEvaluationJob | 每小時 | 備份策略評估（主要由事件驅動） |

## 部署

### Docker (開發)

```bash
# Spring Boot 會自動啟動 Docker Compose 中的 PostgreSQL
./mvnw spring-boot:run
```

### Kubernetes (生產)

本專案使用 GitOps 模式部署到 Kubernetes：

```
git push main
    │
    ├─ GitHub Actions ──▶ Build Docker Image ──▶ Push to ghcr.io
    │                                                │
    └─ k8s/manifests/ ◀── sed update image tag ◀─────┘
           │
           ▼ (ArgoCD auto-sync)
    K8s Cluster
    ├── crypto-monitor-api (Deployment + Service)
    ├── crypto-monitor-postgres (Deployment + PVC)
    ├── crypto-monitor-ingress (Ingress)
    └── ConfigMap / Secrets
```

#### CI/CD 流程

1. `git push main` 觸發 GitHub Actions
2. 構建 Docker image 並推送到 ghcr.io
3. 更新 `k8s/manifests/app.yaml` 中的 image tag 並 push `[skip ci]`
4. ArgoCD 偵測 Git 變動，自動同步到 K8s
5. Smoke test 驗證健康檢查端點

#### K8s 資源

| 資源 | 名稱 | 說明 |
|------|------|------|
| Deployment | `crypto-monitor-api` | Spring Boot 應用 |
| Deployment | `crypto-monitor-postgres` | PostgreSQL 17 |
| Service | `crypto-monitor-api` | port 80 → 8080 |
| Service | `crypto-monitor-postgres` | port 5432 |
| Ingress | `crypto-monitor-ingress` | HTTPS (Cloudflare TLS) |
| PVC | `crypto-monitor-postgres-data` | 20Gi 持久卷 |
| ConfigMap | `crypto-monitor-config` | 非敏感配置 |
| Secret | `crypto-monitor-secrets` | OAuth + DB 密碼 |

#### K8s Spring Profile

生產環境使用 `application-k8s.yml`（`SPRING_PROFILES_ACTIVE=k8s`），覆蓋：
- 資料庫連線指向 K8s 內部 Service
- 禁用 Docker Compose 自動啟動
- 禁用 Mail Health Indicator（避免 liveness probe 因 SMTP 配置缺失而失敗）
- 啟用 Prometheus metrics endpoint

#### 自行部署

1. 建立 K8s namespace 和 secrets
2. 設定 ArgoCD Application 指向 `k8s/manifests/` 目錄
3. 設定 GitHub Actions secrets（`GH_PAT` 用於 push image tag 回 repo）
4. 設定 ghcr.io imagePullSecret
5. 在 Google Cloud Console 新增生產環境的 OAuth Redirect URI

## 效能優化

### 資料庫索引

針對高頻查詢路徑新增了以下索引，提升查詢效能 10~100 倍：

| Entity | 索引 | 用途 |
|--------|------|------|
| `TrackedSymbol` | `idx_tracked_active_status` | 幣對狀態查詢（啟動/排程） |
| `TradePosition` | `idx_position_symbol_status` | 持倉狀態查詢（策略評估） |
| `UserWatchlist` | `idx_watchlist_symbol` | 通知分發（按幣對查訂閱者） |
| `NotificationChannel` | `idx_notif_user_enabled` | 用戶啟用管道查詢（通知發送） |
| `StrategyPerformance` | `idx_perf_template_symbol` | 績效批次查詢（前端列表） |

### 批次查詢優化

K 線資料同步使用批次查詢取代逐筆 `existsBy` 查詢：
- **優化前**：每批 1000 條 K 線 → 1000 次 DB 查詢
- **優化後**：每批 1000 條 K 線 → 1 次批次查詢 + 記憶體過濾

## 日誌配置

日誌按套件分層設定，線程名前綴便於環境過濾：

```
hist-sync-*  → 歷史資料同步
notify-*     → 通知分發
backtest-*   → 回測計算
```

可透過環境變數覆蓋特定模組日誌等級：
```bash
LOGGING_LEVEL_COM_AIINPOCKET_BTCTRADE_SERVICE_NOTIFICATION=DEBUG
```

## License

MIT
