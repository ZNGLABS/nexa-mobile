// CHAINE D'OUTILS — remontee le 13 septembre 2026, et voici pourquoi.
//
// Le premier jalon compilait avec AGP 8.2.2 / Gradle 8.4 / compileSdk 34. L'ajout de
// mobile-wallet-adapter-clientlib-ktx a fait echouer la compilation :
//
//   Dependency 'androidx.activity:activity:1.10.1' requires libraries and applications
//   that depend on it to compile against version 35 or later of the Android APIs.
//   :app is currently compiled against android-34.
//   Also, the maximum recommended compile SDK version for AGP 8.2.2 is 34.
//
// Sept bibliotheques AndroidX, tirees transitivement par MWA, exigent le SDK 35. On
// pouvait les epingler une par une vers des versions plus anciennes ; ce serait sept
// contraintes fragiles a maintenir pour masquer le vrai probleme. On remonte donc la
// chaine, ce qui est la correction franche :
//
//   AGP 8.6.1  → exige Gradle 8.7 minimum, on prend 8.9 dans la CI
//   compileSdk 35, targetSdk laisse a 34 (on ne cible pas un comportement Android 15
//   qu'on n'a aucun moyen de tester sur appareil)
//   Kotlin 1.9.22 inchange, apparie au compilateur Compose 1.5.10
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
