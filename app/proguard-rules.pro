# Gson and Retrofit read generic signatures and runtime annotations reflectively.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepattributes SourceFile,LineNumberTable
-keep,allowobfuscation class com.google.gson.reflect.TypeToken
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken

# JSON/cache DTO class names may change; field names must remain stable across app upgrades.
# Several Canvas fields and legacy cache fields have no @SerializedName annotation.
-keep,allowoptimization,allowobfuscation class com.example.upaos.data.model.**
-keepclassmembers,allowoptimization class com.example.upaos.data.model.** {
    <fields>;
    <init>();
}
-keep,allowoptimization,allowobfuscation class com.example.upaos.data.local.CuentaGuardada
-keepclassmembers,allowoptimization class com.example.upaos.data.local.CuentaGuardada {
    <fields>;
    <init>();
}
-keep,allowoptimization,allowobfuscation class com.example.upaos.data.agenda.AgendaItem
-keepclassmembers,allowoptimization class com.example.upaos.data.agenda.AgendaItem {
    <fields>;
    <init>();
}
-keep,allowoptimization,allowobfuscation class com.example.upaos.util.GitHubAsset
-keepclassmembers,allowoptimization class com.example.upaos.util.GitHubAsset {
    <fields>;
    <init>();
}
-keep,allowoptimization,allowobfuscation class com.example.upaos.util.GitHubRelease
-keepclassmembers,allowoptimization class com.example.upaos.util.GitHubRelease {
    <fields>;
    <init>();
}
-keep,allowoptimization,allowobfuscation class com.example.upaos.service.AsistenciaWorker$SnapshotCurso
-keepclassmembers,allowoptimization class com.example.upaos.service.AsistenciaWorker$SnapshotCurso {
    <fields>;
    <init>();
}
-keep,allowoptimization,allowobfuscation class com.example.upaos.service.GradeSyncCheck$SnapshotNotasCurso
-keepclassmembers,allowoptimization class com.example.upaos.service.GradeSyncCheck$SnapshotNotasCurso {
    <fields>;
    <init>();
}

# Retrofit implements this annotated interface dynamically. Its methods/signatures are API contracts.
-keep,allowoptimization interface com.example.upaos.data.api.CanvasApiService { public *; }

# WorkManager persists fully qualified worker names in its database between app versions.
-keep,allowoptimization class com.example.upaos.service.*Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
