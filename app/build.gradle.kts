plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // OkHttp plutot que HttpURLConnection : gestion des delais, des reprises et du
    // pool de connexions deja eprouvee, ce qui compte pour un service qui tourne en
    // fond toutes les minutes.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Mobile Wallet Adapter — l'implementation officielle de Solana Mobile.
    // L'application ne detient JAMAIS de cle privee : elle demande au portefeuille
    // installe (Phantom, Solflare, Seed Vault du Seeker) d'autoriser et de signer.
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.8")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
