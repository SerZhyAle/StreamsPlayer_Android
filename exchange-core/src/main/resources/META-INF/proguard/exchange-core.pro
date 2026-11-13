# Gson descriptor and watch-account persistence across app updates.
-keepattributes Signature,*Annotation*
-keep class com.sza.streamsplayer.data.broadcast.Broadcast*Dto { *; }
-keep class com.sza.streamsplayer.data.exchange.ExchangeAccountEntry { *; }
-keep enum com.sza.streamsplayer.data.exchange.ExchangeAccountState { *; }
