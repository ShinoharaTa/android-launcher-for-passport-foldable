# Fold8 Launcher

Galaxy Z Fold8(パスポート型)向けの自作ホームランチャー。
背景・設計は `docs/` を、見た目の方向性は `mock/` を参照。

## ビルドと実行

システムの `java` は実行環境を持たないので、Android Studio 同梱の JBR を使う。

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug
adb shell cmd package set-home-activity net.shino3.gzf8launcher/.MainActivity
```

エミュレータ(AVD `fold`)で折りたたみ状態を切り替える:

```bash
adb shell cmd device_state state 1   # CLOSED
adb shell cmd device_state state 3   # OPENED
adb shell cmd device_state state reset
```

## モック UI

```bash
python3 -m http.server 8814 --directory mock
# http://localhost:8814/index.html
```
