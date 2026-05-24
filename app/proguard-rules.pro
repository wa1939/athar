# Athar release proguard rules — minimal; module-level keeps live in consumer-rules.pro.

# Compose / Material 3 — already handled by AGP; no extra keeps required.

# Kotlinx serialization — keep generated serializers.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclasseswithmembers class <1>$$serializer { *; }

# Hilt entry points and bindings are kept by Hilt's own proguard rules.
