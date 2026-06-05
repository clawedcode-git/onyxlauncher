# Retain launcher Activity and Service names referenced in Manifest
-keep class com.onyxlauncher.ui.MainActivity { *; }
-keep class com.onyxlauncher.wallpaper.service.OnyxWallpaperService { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Protobuf lite
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Coil
-dontwarn okhttp3.**
-dontwarn okio.**
