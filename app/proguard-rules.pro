# kotlinx.serialization: Serializer der Datenklassen behalten.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class de.kopfschmerztagebuch.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class de.kopfschmerztagebuch.** {
    kotlinx.serialization.KSerializer serializer(...);
}
