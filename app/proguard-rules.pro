# Keep Room database entities and DAOs
-keep class com.fabiantorrestech.visualtimerplus.db.** { *; }

# Keep TimerRepository state enums (used via reflection in SharedPreferences migration)
-keepnames enum com.fabiantorrestech.visualtimerplus.timer.** { *; }

# Keep Kotlin serialisation helpers used by coroutines/StateFlow
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Standard Android rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
