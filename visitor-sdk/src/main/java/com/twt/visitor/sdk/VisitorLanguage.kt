package com.twt.visitor.sdk

/** Languages supported by the visitor page; [queryValue] is the stable URL parameter value. */
enum class VisitorLanguage(val queryValue: String) {
    EN("en"), ZH_CN("zh-cn"), ZH_TW("zh-tw"), JA("ja"), KO("ko"),
    DE("de"), FR("fr"), PT("pt"), RU("ru"), ES("es"), VI("vi"),
    TH("th"), ID("id"), MS("ms"), TL("tl"),
}
