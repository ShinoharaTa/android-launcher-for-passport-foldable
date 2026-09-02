package net.shino3.gzf8launcher.widget

/** 同梱の種別をまとめて登録する。アプリ起動時に一度呼ぶ。 */
object BuiltInWidgets {
    fun register() {
        WidgetRegistry.register(ClockWidget.widget)
        WidgetRegistry.register(StatusWidget.widget)
        WidgetRegistry.register(UsageWidget.widget)
        WidgetRegistry.register(MetricsWidget.widget)
    }
}
