CREATE TABLE kline (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(20)    NOT NULL,
    interval_type   VARCHAR(10)    NOT NULL,
    open_time       TIMESTAMPTZ    NOT NULL,
    close_time      TIMESTAMPTZ    NOT NULL,
    open_price      NUMERIC(20,8)  NOT NULL,
    high_price      NUMERIC(20,8)  NOT NULL,
    low_price       NUMERIC(20,8)  NOT NULL,
    close_price     NUMERIC(20,8)  NOT NULL,
    volume          NUMERIC(30,8)  NOT NULL,
    quote_volume    NUMERIC(30,8),
    trade_count     INTEGER,
    CONSTRAINT uq_kline_symbol_interval_time UNIQUE (symbol, interval_type, open_time)
);

CREATE INDEX idx_kline_symbol_interval_open_time ON kline (symbol, interval_type, open_time);

CREATE TABLE trade_position (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(20)    NOT NULL,
    direction       VARCHAR(10)    NOT NULL,
    status          VARCHAR(30)    NOT NULL,
    entry_price     NUMERIC(20,8)  NOT NULL,
    entry_time      TIMESTAMPTZ    NOT NULL,
    exit_price      NUMERIC(20,8),
    exit_time       TIMESTAMPTZ,
    quantity        NUMERIC(20,8)  NOT NULL,
    capital_used    NUMERIC(20,2)  NOT NULL,
    stop_loss_price NUMERIC(20,8)  NOT NULL,
    realized_pnl    NUMERIC(20,2),
    return_pct      NUMERIC(10,4),
    exit_reason     VARCHAR(30),
    is_backtest     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_position_status ON trade_position (status);
CREATE INDEX idx_position_entry_time ON trade_position (entry_time);

CREATE TABLE trade_signal (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(20)    NOT NULL,
    signal_time     TIMESTAMPTZ    NOT NULL,
    action          VARCHAR(20)    NOT NULL,
    close_price     NUMERIC(20,8)  NOT NULL,
    ema_short       NUMERIC(20,8),
    ema_long        NUMERIC(20,8),
    rsi_value       NUMERIC(10,4),
    macd_value      NUMERIC(20,8),
    macd_signal     NUMERIC(20,8),
    macd_histogram  NUMERIC(20,8),
    is_backtest     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signal_time ON trade_signal (signal_time);
CREATE INDEX idx_signal_action ON trade_signal (action);
