# Release builds are shrunk with R8. The libraries carry their own consumer rules
# (kotlinx.serialization keeps every @Serializable class's generated serializer,
# OneSignal, PostHog and Tink keep what they reflect on), so the app needs very little.

# Readable crash reports in PostHog error tracking: keep file names and line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# WorkManager (pulled in by OneSignal) builds its Room database reflectively from the generated
# `<Name>_Impl` class; without this the release build crashes at startup in InitializationProvider.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
