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

`installDebug` で再インストールするたびに既定ホームの設定が外れることがあるので、
そのときは `set-home-activity` を再実行する。

### エミュレータでの動作確認の小技

```bash
# 使用状況アクセス(規則つきフォルダ、USAGE ウィジェット)を許可する
adb shell appops set net.shino3.gzf8launcher GET_USAGE_STATS allow

# 保存済みレイアウトを捨てて同梱プリセットからやり直す
adb shell run-as net.shino3.gzf8launcher rm -f files/layout.json
adb shell am force-stop net.shino3.gzf8launcher

# 長押しドラッグ(input motionevent を別々に送ると長押しが成立しないので draganddrop を使う)
adb shell input draganddrop <x1> <y1> <x2> <y2> 1200
# 同じ座標を指定すると長押しメニューになる
```

## モック UI

```bash
python3 -m http.server 8814 --directory mock
# http://localhost:8814/index.html
```
