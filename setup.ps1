Write-Host "Initializing BlackRise Client Project..." -ForegroundColor Cyan

# ساخت ساختار دقیق پوشه‌ها
New-Item -ItemType Directory -Force -Path "app\src\main\java\ir\blackrise\client" | Out-Null
New-Item -ItemType Directory -Force -Path ".github\workflows" | Out-Null

# ۱. فایل build.gradle.kts
$gradleContent = @'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.blackrise.client"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.blackrise.client"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
'@
Set-Content -Path "app\build.gradle.kts" -Value $gradleContent

# ۲. فایل MainActivity.kt
$mainActivityContent = @'
package ir.blackrise.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlackRiseTheme { MainScreen() }
        }
    }
}

@Composable
fun BlackRiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF09090B),
            surface = Color(0xFF141417),
            primary = Color(0xFFF97316),
            secondary = Color(0xFF27272A),
            onBackground = Color(0xFFFAFAFA),
            onSurface = Color(0xFFFAFAFA)
        ),
        content = content
    )
}

@Composable
fun MainScreen() {
    var isConnected by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf("FA") }
    var selectedTab by remember { mutableStateOf(0) }

    val texts = mapOf(
        "FA" to mapOf("title" to "صنایع بلک‌رایز", "connect" to "لمس برای اتصال", "connected" to "متصل و ایمن", "servers" to "سرورها", "settings" to "تنظیمات"),
        "EN" to mapOf("title" to "BLACKRISE", "connect" to "TAP TO CONNECT", "connected" to "SECURE & ACTIVE", "servers" to "Servers", "settings" to "Settings"),
        "RU" to mapOf("title" to "BLACKRISE", "connect" to "НАЖМИТЕ", "connected" to "ПОДКЛЮЧЕНО", "servers" to "Серверы", "settings" to "Настройки"),
        "CN" to mapOf("title" to "BLACKRISE", "connect" to "点击连接", "connected" to "已连接", "servers" to "服务器", "settings" to "设置")
    )
    val t = texts[currentLanguage]!

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF141417)) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(t["connect"] ?: "") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFF97316))
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    label = { Text(t["servers"] ?: "") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFF97316))
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(t["settings"] ?: "") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFF97316))
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)).padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = t["title"] ?: "BLACKRISE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF141417)).border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp)).padding(4.dp)) {
                    listOf("FA", "EN", "RU", "CN").forEach { lang ->
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (currentLanguage == lang) Color(0xFFF97316) else Color.Transparent).clickable { currentLanguage = lang }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(text = lang, color = if (currentLanguage == lang) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            if (selectedTab == 0) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141417)), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Core & Protocol", color = Color.Gray, fontSize = 12.sp); Text("Xray / Sing-box", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                            Column(horizontalAlignment = Alignment.End) { Text("Latency", color = Color.Gray, fontSize = 12.sp); Text("14 ms", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                    }
                    Spacer(modifier = Modifier.height(60.dp))
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp).clip(CircleShape).background(Brush.radialGradient(colors = if (isConnected) listOf(Color(0xFFF97316).copy(alpha = 0.4f), Color(0xFF141417)) else listOf(Color(0xFF27272A), Color(0xFF141417)))).border(2.dp, if (isConnected) Color(0xFFF97316) else Color(0xFF27272A), CircleShape).clickable { isConnected = !isConnected }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = if (isConnected) Icons.Default.Lock else Icons.Default.PowerSettingsNew, contentDescription = null, tint = if (isConnected) Color(0xFFF97316) else Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = if (isConnected) t["connected"]!! else t["connect"]!!, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else if (selectedTab == 1) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Server Arsenal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141417)), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Frankfurt Node #1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("VLESS Reality", color = Color.Gray, fontSize = 12.sp) }
                            Text("14 ms", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Client Configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF141417), RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Memory Optimizer", color = Color.White)
                        Text("Active", color = Color.Gray)
                    }
                }
            }
        }
    }
}
'@
Set-Content -Path "app\src\main\java\ir\blackrise\client\MainActivity.kt" -Value $mainActivityContent

# ۳. فایل کانفیگ GitHub Actions
$workflowContent = @'
name: Build Android APK
on:
  push:
    branches: [ "main" ]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    - name: Build Debug APK
      run: ./gradlew assembleDebug
    - name: Upload APK Artifact
      uses: actions/upload-artifact@v4
      with:
        name: BlackRise-Client-APK
        path: app/build/outputs/apk/debug/app-debug.apk
'@
Set-Content -Path ".github\workflows\build.yml" -Value $workflowContent

Write-Host "Files created successfully! Pushing to GitHub..." -ForegroundColor Green
git init
git branch -M main
git remote add origin https://github.com/DevianARTECH/BlackRiseClient.git
git add .
git commit -m "Automated BlackRise Client setup"
git push -u origin main --force

Write-Host "Done! Check your GitHub Actions tab to download the APK." -ForegroundColor Green