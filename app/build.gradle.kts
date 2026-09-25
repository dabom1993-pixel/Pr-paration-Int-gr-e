plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adf.pirobinetterie"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.adf.pirobinetterie"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
