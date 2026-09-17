plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Depuis Kotlin 2.0, le compilateur Compose est livre avec le plugin Kotlin et se
    // declare ici. Le bloc composeOptions / kotlinCompilerExtensionVersion de l'epoque
    // Kotlin 1.9 n'existe plus.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.nexaexchange.mobile"
    // 35 est impose par les AndroidX que tire Mobile Wallet Adapter — voir la note
    // detaillee dans le build.gradle.kts racine.
    compileSdk = 35

    defaultConfig {
        // Identifiant VOLONTAIREMENT different de fr.nexaexchange.dex, l'application
        // deja publiee sur le Solana dApp Store. Les deux peuvent ainsi cohabiter sur
        // le meme Seeker : indispensable pour demontrer la nouvelle couche native a
        // cote de l'ancienne pendant le jury.
        applicationId = "fr.nexaexchange.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }

        // Empreinte du commit, injectee par la CI (-PbuildSha=...).
        //
        // POURQUOI : le 13 septembre 2026, le testeur a desinstalle puis reinstalle
        // l'application depuis un APK deja present dans sa conversation Telegram, envoye
        // quatorze minutes avant que le correctif ne soit publie. Il a donc reteste la
        // version AVANT correction et conclu que rien n'avait change. Deux APK successifs
        // pesent le meme poids a l'arrondi : rien, a l'ecran, ne permettait de les
        // distinguer. Desormais l'application affiche le commit dont elle est issue.
        buildConfigField(
            "String",
            "BUILD_SHA",
            "\"" + (project.findProperty("buildSha") ?: "local") + "\"",
        )
    }

    buildTypes {
        release {
            // AUCUN SECRET DANS CE DEPOT. Il est public : le depot prive
            // ZNGLABS/nexa-exchange-apk contient un mot de passe de keystore en clair,
            // raison pour laquelle il reste prive et pour laquelle celui-ci repart de
            // zero. Les APK du hackathon sont signes avec la cle de debogage generee
            // par la CI : ils s'installent parfaitement pour une demonstration et
            // n'exposent rien.
            signingConfig = signingConfigs.getByName("debug")
            // R8 desactive tant qu'on n'a pas de banc de test sur appareil : une regle
            // manquante casse Compose a l'execution, pas a la compilation, et on ne le
            // verrait qu'apres coup.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true   // requis pour buildConfigField depuis AGP 8
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // BOM contemporain de Kotlin 2.1.21 et d'AGP 8.10.1 : les trois ont ete publies
    // en mai 2025, donc testes ensemble en amont.
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // androidx.work RETIRE le 17 septembre 2026 — il n'a jamais servi.
    //
    // La surveillance tourne dans un service de premier plan, relance par BootReceiver :
    // aucune ligne du projet n'importe WorkManager. La bibliotheque etait tout de meme
    // embarquee, avec son initialiseur, et surtout elle ajoutait au manifeste fusionne :
    //
    //     uses-permission#android.permission.WAKE_LOCK
    //     ADDED from [androidx.work:work-runtime:2.9.0]
    //
    // Une permission de plus dans la liste que lit l'utilisateur, pour du code mort.
    // La regle appliquee ici : une dependance qu'on n'utilise pas est une dependance
    // qu'on ne devrait pas expedier.

    // OkHttp plutot que HttpURLConnection : gestion des delais, des reprises et du
    // pool de connexions deja eprouvee, ce qui compte pour un service qui tourne en
    // fond toutes les minutes.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Mobile Wallet Adapter — l'implementation officielle de Solana Mobile.
    // L'application ne detient JAMAIS de cle privee : elle demande au portefeuille
    // installe (Phantom, Solflare, Seed Vault du Seeker) d'autoriser et de signer.
    //
    // 🔴 LES EXCLUSIONS NE SONT PAS COSMETIQUES — trouve le 17 septembre 2026 en lisant
    // le manifeste FUSIONNE de l'APK, pas le notre.
    //
    // MWA 2.0.8 declare `androidx.test:core`, `junit` et `hamcrest` en portee normale et
    // non en portee de test. Ces bibliotheques partaient donc dans l'APK de PRODUCTION,
    // et `androidx.test:core` y ajoutait une permission que nous n'avons jamais demandee :
    //
    //     uses-permission#android.permission.REORDER_TASKS
    //     ADDED from [androidx.test:core:1.6.1]
    //
    // Un moniteur de prix n'a aucune raison de pouvoir reordonner les taches du systeme,
    // et un utilisateur qui lit la liste des permissions n'a aucun moyen de savoir d'ou
    // elle sort. Du code de test embarque en production, c'est en plus de la surface
    // d'attaque offerte pour rien.
    //
    // VERIFIE AVANT D'EXCLURE : aucune classe des trois jars de MWA 2.0.8 ne reference
    // `androidx/test`, `org/junit` ni `org/hamcrest`. La dependance est declaree mais
    // jamais utilisee — retirer ces bibliotheques ne peut donc pas provoquer de
    // NoClassDefFoundError a l'execution. C'est une erreur d'emballage chez l'editeur,
    // pas une dependance reelle.
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.8") {
        exclude(group = "androidx.test")
        exclude(group = "androidx.test.ext")
        exclude(group = "junit")
        exclude(group = "org.hamcrest")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
