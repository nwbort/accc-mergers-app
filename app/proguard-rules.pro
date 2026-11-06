# kotlinx.serialization ships keep rules for @Serializable classes; keep the
# data models whole anyway, since they mirror mergers.fyi's JSON by name.
-keep class fyi.mergers.app.data.** { *; }

# Room (inside WorkManager) creates its database's generated _Impl class
# reflectively through its no-argument constructor, which R8 full mode strips
# unless told otherwise.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
