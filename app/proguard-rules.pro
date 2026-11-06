# kotlinx.serialization keeps generated serializers via its own consumer rules; DTOs only need their names kept.
-keep,includedescriptorclasses class com.pos.kiosk.data.remote.dto.** { *; }

# iMin printer SDKs are plain jars, so they bring no consumer rules of their own: SDK 2.0 talks to the print service
# through AIDL stubs, SDK 1.0 loads its native serial port library by name.
-keep class com.imin.printer.** { *; }
-keep class com.imin.printerlib.** { *; }
-keep class com.imin.print.** { *; }
-keep class android.serialport.** { *; }
