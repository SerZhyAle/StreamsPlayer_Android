# Gson persistence and Data Layer wire payloads must survive R8 field renaming.
-keepattributes Signature,*Annotation*
-keepclassmembers class com.sza.streamsplayer.wear.domain.model.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.sza.streamsplayer.wear.domain.model.WearStreamChannel { *; }
-keep class com.sza.streamsplayer.wear.domain.model.WearStreamCollection { *; }
-keep class com.sza.streamsplayer.wear.domain.model.WearStreamUsage { *; }
-keep class com.sza.streamsplayer.wear.domain.model.WearTileTargetRef$* { *; }
-keep class com.sza.streamsplayer.wear.data.broadcast.** { *; }
-keep class com.sza.streamsplayer.wear.domain.model.WearStreamPinsDeltaItem { *; }

-keep class com.sza.streamsplayer.wear.domain.model.** { *; }
-keep class com.sza.streamsplayer.wear.data.wear.WearExchangeHandoverPayload { *; }
-keep class com.sza.streamsplayer.wear.data.wear.WearExchangeHandoverAck { *; }
-keep class com.sza.streamsplayer.wear.data.wear.WearExchangeLeaveReceiver$Request { *; }
-keep class com.sza.streamsplayer.wear.data.wear.WearExchangeLeaveReceiver$Ack { *; }
-keep class com.sza.streamsplayer.wear.data.exchange.WearExchangeEntryStore$Snapshot { *; }
