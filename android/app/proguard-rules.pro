# MVP 阶段未开启代码混淆（isMinifyEnabled = false）。
# 启用时保留 Compose 与反射相关规则，并确认 docx 解析（org.xmlpull / java.util.zip）不受影响。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.**
