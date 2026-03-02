# Room entities and DAOs must be kept
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# SQLCipher native libraries
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Room type converters
-keep class com.esiri.esiriplus.core.database.converter.** { *; }
