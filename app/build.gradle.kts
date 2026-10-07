plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.mireli.driver"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.error302.mireli.driver"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.5.0"
        buildConfigField("String", "DRIVER_SERVICE_URL", "\"https://mireli-tau.vercel.app\"")
        buildConfigField("boolean", "DRIVER_SERVICE_TEST", "false")
        val mapStyleUrl = providers.gradleProperty("MIRELI_MAP_STYLE_URL").orElse("https://tiles.openfreemap.org/styles/liberty").get()
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "MAP_STYLE_URL", "\"$mapStyleUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            resValue("string", "app_name", "Mireli Preview")
        }
        create("production") {
            dimension = "environment"
            resValue("string", "app_name", "Mireli Driver")
        }
        create("pilot") {
            dimension = "environment"
            applicationIdSuffix = ".pilot"
            versionNameSuffix = "-pilot"
            resValue("string", "app_name", "Mireli Driver")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "Mireli Staging")
            buildConfigField("String", "DRIVER_SERVICE_URL", "\"http://10.0.2.2:3100\"")
            buildConfigField("boolean", "DRIVER_SERVICE_TEST", "true")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true; resValues = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.08.01")
    implementation(bom)
    androidTestImplementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
