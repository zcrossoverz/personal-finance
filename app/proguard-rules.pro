# kotlinx.serialization backup models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class dev.personal.ledger.backup.** { *** Companion; }
-keepclasseswithmembers class dev.personal.ledger.backup.** { kotlinx.serialization.KSerializer serializer(...); }
