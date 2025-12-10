    plugins {
        alias(libs.plugins.android.application)
        alias(libs.plugins.kotlin.android)
    }

    android {
        namespace = "com.example.instacartDummy"
        compileSdk = 34

        defaultConfig {
            applicationId = "com.example.instacartDummy"
            minSdk = 24
            targetSdk = 34
            versionCode = 1
            versionName = "1.0"

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {
            release {
                isMinifyEnabled = true // Enable code shrinking and obfuscation
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        kotlinOptions {
            jvmTarget = "1.8"
        }
        buildFeatures {
            viewBinding = true
        }
    }

    dependencies {
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)
        implementation(libs.androidx.constraintlayout)
        implementation(libs.androidx.navigation.fragment.ktx)
        implementation(libs.androidx.navigation.ui.ktx)
        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        implementation("androidx.room:room-runtime:2.5.2")
        implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
        androidTestImplementation(libs.androidx.espresso.core)
        implementation ("androidx.core:core-ktx:1.7.0")
        implementation ("androidx.appcompat:appcompat:1.4.1")
        implementation ("com.google.android.material:material:1.5.0")
        implementation ("androidx.constraintlayout:constraintlayout:2.1.3")
        implementation ("androidx.recyclerview:recyclerview:1.2.1")

        testImplementation ("junit:junit:4.13.2")
        androidTestImplementation ("androidx.test.ext:junit:1.1.3")
        androidTestImplementation ("androidx.test.espresso:espresso-core:3.4.0")

    }
