# Release Procedure (野良配布)

## 1. keystore生成

keytool -genkey -v -keystore release.keystore ...

## 2. Gradle signingConfig設定

release {
    signingConfig signingConfigs.release
}

## 3. APK生成

./gradlew assembleRelease

## 4. GitHub Releasesへアップロード

- APK
- CHANGELOG
