package com.aiinpocket.btctrade.model.enums;

/**
 * 通知管道類型。
 * 每種類型對應一個 NotificationSender 實作，
 * 使用者可以為每種類型設定不同的接收端資訊。
 */
public enum ChannelType {

    /** Discord — 透過 Bot Token 發送訊息到頻道或個人 DM */
    DISCORD,

    /** Gmail — 透過系統預設的 SMTP 發送郵件到使用者指定的收件人 */
    GMAIL,

    /** Telegram — 透過使用者自己的 Bot Token 發送訊息到指定的 Chat ID */
    TELEGRAM
}
