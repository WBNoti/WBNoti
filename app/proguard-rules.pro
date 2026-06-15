# Strip all log calls in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Keep service class names so Android can bind them; R8 can rename internal members
-keep class com.wbnoti.NotificationService
-keep class com.wbnoti.WBAccessibilityService

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
