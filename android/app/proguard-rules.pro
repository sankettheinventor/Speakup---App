# ============================================================================
# SnapJar — R8 keep rules. We shrink the big unused ML Kit / Play-Services / AndroidX
# code, but KEEP everything the Capacitor JS bridge reaches by reflection (plugins +
# @PluginMethod methods are called BY NAME from JavaScript, so they must not be renamed
# or removed).
# ============================================================================

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, Exceptions

# ---- Our own native plugins (registered via registerPlugin(...) + called by name) ----
-keep class com.snapjar.app.** { *; }

# ---- Capacitor core + every plugin class and their bridge-invoked members ----
-keep class com.getcapacitor.** { *; }
-keep public class * extends com.getcapacitor.Plugin { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }
-keepclassmembers class * { @com.getcapacitor.PluginMethod public <methods>; }
-keepclassmembers class * { @com.getcapacitor.annotation.PermissionCallback <methods>; }
-keepclassmembers class * { @com.getcapacitor.annotation.ActivityCallback <methods>; }
-dontwarn com.getcapacitor.**

# ---- Cordova compat layer pulled in by capacitor-cordova-android-plugins ----
-keep class org.apache.cordova.** { *; }
-dontwarn org.apache.cordova.**

# ---- JS interfaces (anything exposed to the WebView) ----
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- Google Mobile Ads SDK ----
# Banner/interstitial (via @capacitor-community/admob) AND our native AppOpenPlugin reference
# these classes directly. GMA ships consumer rules, but keep them explicitly so R8 can never
# strip/rename the ad classes (esp. com.google.android.gms.ads.appopen.* used by App Open).
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
