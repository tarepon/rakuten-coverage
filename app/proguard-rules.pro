# Room エンティティ・DAO はリフレクションで参照されるため保持する
-keep class com.example.rakutencoverage.data.** { *; }

# Kotlin data class の toString / copy / componentN を保持
-keepclassmembers class com.example.rakutencoverage.** {
    public synthetic <methods>;
}
