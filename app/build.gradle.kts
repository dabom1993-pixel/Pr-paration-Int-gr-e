plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version = numéro du run GitHub Actions : il augmente à chaque compilation. Il est
// aussi publié dans version.txt à côté de l'APK, ce qui permet à l'app et à ADF TAR de savoir
// si la version installée est la dernière (même référence pour les deux).
val numeroVersion = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.adf.pirobinetterie"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.adf.pirobinetterie"
        minSdk = 24
        targetSdk = 34
        versionCode = numeroVersion
        versionName = "1.$numeroVersion"
    }

    signingConfigs {
        // Clé de debug fixe (générée une fois par le workflow GitHub Actions et
        // committée dans le dépôt) afin que chaque nouvelle APK puisse s'installer
        // par-dessus la précédente sans devoir désinstaller l'app à chaque mise à jour.
        getByName("debug") {
            val debugKeystore = file("debug.keystore")
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    // Deux versions de l'application, construites depuis le même code :
    // - "beta"   : version de test (installée à côté de la finale, données séparées,
    //              nom "PI Robinetterie BETA", release GitHub "tablette-beta") ;
    // - "finale" : version accessible à tous (release GitHub "tablette-latest").
    flavorDimensions += "canal"
    productFlavors {
        create("finale") {
            dimension = "canal"
            buildConfigField("String", "CANAL_MAJ", "\"tablette-latest\"")
            buildConfigField("String", "NOM_APK", "\"PIRobinetterie.apk\"")
            buildConfigField("boolean", "BETA", "false")
        }
        create("beta") {
            dimension = "canal"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("String", "CANAL_MAJ", "\"tablette-beta\"")
            buildConfigField("String", "NOM_APK", "\"PIRobinetterie-BETA.apk\"")
            buildConfigField("boolean", "BETA", "true")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }
}

// Volontairement aucune bibliothèque externe : l'application n'utilise que le framework
// Android (écrans, PDF, SQLite inutile : un simple fichier JSON suffit) et la lecture/écriture
// directe du fichier Excel (archive ZIP + XML). Moins de dépendances = compilation plus sûre.
dependencies {
}
