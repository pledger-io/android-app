# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class com.pledgerio.app.data.remote.dto.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt
-keep class com.pledgerio.app.PledgerApp { *; }
-keep class com.pledgerio.app.Hilt_PledgerApp { *; }
-keep class com.pledgerio.app.**_HiltComponents_* { *; }
-keep class dagger.hilt.** { *; }
