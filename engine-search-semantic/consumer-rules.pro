# F8.22 · LiteRT-LM (litertlm-android). L'AAR non porta regole proprie: la parte nativa (liblitertlm_jni.so) chiama per
# nome classi, metodi e campi di Kotlin (le risposte, le opzioni, le eccezioni). Con nomi offuscati o membri tolti il
# motore fallisce a runtime. Si tiene il pacchetto intero, come MyCloud Gallery 2.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep interface com.google.ai.edge.litertlm.** { *; }
# kotlin-reflect e' tolto dalle dipendenze (build.gradle): lo usa solo l'API degli strumenti dei modelli di linguaggio.
-dontwarn kotlin.reflect.**
-dontwarn kotlin.reflect.full.**
