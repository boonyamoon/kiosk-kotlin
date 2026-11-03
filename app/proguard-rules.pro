# kotlinx.serialization keeps generated serializers via its own consumer rules; DTOs only need their names kept.
-keep,includedescriptorclasses class com.pos.kiosk.data.remote.dto.** { *; }
