package com.aiinpocket.btctrade.model.enums;

/**
 * 回測執行狀態枚舉。
 * 追蹤用戶自訂策略回測的生命週期，從排隊到完成。
 *
 * <ul>
 *   <li>PENDING — 等待執行（已排入隊列，尚未分配執行緒）</li>
 *   <li>RUNNING — 執行中（已分配到 backtestExecutor 執行緒池中運算）</li>
 *   <li>COMPLETED — 完成（結果已序列化儲存到 resultJson 欄位）</li>
 *   <li>FAILED — 失敗（執行過程中拋出例外，錯誤訊息記錄在 resultJson）</li>
 * </ul>
 */
public enum BacktestRunStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
