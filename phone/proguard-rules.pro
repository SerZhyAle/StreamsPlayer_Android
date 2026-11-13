# Keep rules of the phone app. Empty on purpose: the skeleton has nothing that R8's defaults and the
# consumer rules of Hilt and AndroidX do not already cover. A rule is added with the class that needs it.

# The phone and watch exchange mirrored Gson DTOs with pinned wire names.
-keepattributes Signature,*Annotation*
-keep class com.sza.streamsplayer.domain.model.Wear* { *; }
-keep class com.sza.streamsplayer.data.exchange.WatchExchangeLeaveRequest { *; }
-keep class com.sza.streamsplayer.data.exchange.WatchExchangeLeaveAck { *; }

# Channel actions retain a Gson snapshot in Fragment arguments across recreation.
-keep class com.sza.streamsplayer.data.local.db.StreamSourceEntity { *; }
