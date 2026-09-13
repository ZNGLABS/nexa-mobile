// CHAINE D'OUTILS — refaite le 13 septembre 2026 apres DEUX compilations echouees.
//
// Ce qui s'est passe, dans l'ordre, parce que la lecon vaut d'etre gardee :
//
// 1. Le premier jalon compilait en AGP 8.2.2 / Kotlin 1.9.22 / compileSdk 34.
//    L'ajout de mobile-wallet-adapter-clientlib-ktx a casse la compilation :
//      « androidx.activity:activity:1.10.1 requires [...] version 35 or later »
//    Sept bibliotheques AndroidX tirees par MWA exigent le SDK 35.
//
// 2. J'ai remonte a AGP 8.6.1 / compileSdk 35 en gardant Kotlin 1.9.22. Nouvel echec,
//    351 erreurs :
//      « mobile-wallet-adapter-clientlib-ktx-2.0.8 was compiled with an incompatible
//        version of Kotlin. The binary version of its metadata is 2.1.0, expected 1.9.0 »
//    MWA 2.0.8 est compile avec Kotlin 2.1. Un compilateur 1.9 ne sait pas lire ses
//    metadonnees.
//
// 3. J'ai alors VERIFIE les versions publiees au lieu de les supposer, en interrogeant
//    Maven Central et le depot Google cote serveur. Mes deux premiers choix venaient de
//    connaissances perimees : AGP en est a la serie 9.x, Kotlin a 2.4.
//
// Ensemble retenu : la generation mai 2025, dont les quatre briques ont ete publiees
// et testees ensemble par leurs editeurs, et dont la syntaxe de build est celle
// d'AGP 8.x — celle dans laquelle ces fichiers sont ecrits. Passer directement a
// AGP 9.x aurait ajoute des changements de DSL non testables ici a un probleme deja
// resolu.
//
//   AGP 8.10.1 · Kotlin 2.1.21 · Gradle 8.14.5 · Compose BOM 2025.05.01 · compileSdk 35
//
// Depuis Kotlin 2.0, le compilateur Compose fait partie du plugin Kotlin : on declare
// org.jetbrains.kotlin.plugin.compose et composeOptions disparait.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
}
