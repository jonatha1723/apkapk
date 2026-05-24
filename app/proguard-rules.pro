# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-repackageclasses ''
-allowaccessmodification
-keepparameternames

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-obfuscationdictionary obfuscation_dictionary.txt
-classobfuscationdictionary obfuscation_dictionary.txt
-packageobfuscationdictionary obfuscation_dictionary.txt
