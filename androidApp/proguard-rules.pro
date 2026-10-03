# R8 для релиза DJMetry. Библиотеки (kotlinx.serialization, Ktor, OkHttp, Firebase, Compose, AndroidX)
# приносят свои правила; здесь — только то, что они не покрывают.

# Модели API: сериализаторы генерирует плагин kotlinx.serialization; сохраняем их и companion-объекты моделей
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class com.djmetry.** {
    *** Companion;
    static ** $serializer;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.djmetry.**$$serializer { *; }

# MapLibre: нативная часть вызывает Java/Kotlin-классы через JNI по именам
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Активити и сервис пушей объявлены в манифесте (R8 их и так держит), Application — тоже
-keep class com.djmetry.android.** { *; }

# Ktor и его зависимости тянут необязательные классы, которых нет на Android
-dontwarn org.slf4j.**
-dontwarn io.ktor.util.debug.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
