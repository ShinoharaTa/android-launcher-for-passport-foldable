# Galaxy Z Fold 8 ハードウェア・ソフトウェア仕様

自作ホームランチャーの設計判断に必要な範囲で、Galaxy Z Fold 8 の仕様をまとめる。
公開情報から確定できたものと、実機で測らないと確定しないものを分けて記す。
実機確認が必要な項目には **要実測** と付けた。

## 対象機種の確定

2026年7月22日の Unpacked で、Galaxy Z Fold8、Galaxy Z Fold8 Ultra、Galaxy Z Flip8 の3機種が発表された。
このうち Fold8 と Fold8 Ultra は形状が根本的に異なり、ランチャーの設計を共有できない。

- **Galaxy Z Fold8**：パスポート型。カバー 5.5インチ 10:16、メイン 7.6インチ 4:3。
- **Galaxy Z Fold8 Ultra**：従来型の縦長 Fold。カバー 6.5インチ 2520×1080、メイン 8インチ 2504×2256。

本書は Fold8（無印、パスポート型）を対象とする。
Ultra を併せて対象にするなら、姿勢の扱いとグリッド設計をもう一系統用意する必要がある（後述の Flex Mode の差が効く）。

## 折りたたみ方向と画面の向き

ここが従来の Fold と最も違う点であり、ランチャーの設計を丸ごと決める。

Fold8 は**横に開く**。
折りたたんだ状態で縦123.9mm × 横81.9mm、開くと縦123.9mm × 横161.4mm になる。
つまり高さは変わらず、横幅だけが約2倍になる。
折りたたみ時の外形はパスポート（125×88mm）とほぼ同じで、製品名の由来もそこにある。

この結果、二つの画面はこうなる。

- **カバー画面**：縦向き。ただし従来のスマートフォン（9:20 前後）よりはるかに短く広い 10:16。
- **メイン画面**：**横向きが自然な向き**の 4:3。縦向きは回転して使う副次的な状態。

物理寸法で確かめると整合する。
カバー画面は約74mm × 約117mm、メイン画面は約154mm × 約116mm。
カバーの高さ117mmとメインの短辺116mmがほぼ一致し、メインの長辺154mmがカバーの幅74mmの約2倍にあたる。

既存のホームランチャーは、いずれも「縦長のスマートフォンを開くと縦長のタブレットになる」前提で作られている。
Fold8 ではその前提が成り立たない。
メイン画面のホームは横長キャンバスを一次状態として設計することになる。

## ディスプレイ諸元

| | カバー | メイン |
| --- | --- | --- |
| サイズ | 5.5インチ | 7.6インチ |
| 解像度 | 1248 × 1972 px | 2448 × 1848 px（横向き時） |
| アスペクト比 | 10:16（≒1:1.58） | 4:3（≒1.33:1） |
| 画素密度 | 約428 ppi | 約404 ppi |
| 物理寸法 | 約74 × 117 mm | 約154 × 116 mm |
| パネル | Dynamic LTPO AMOLED 2X, 1–120Hz | Dynamic LTPO AMOLED 2X, 1–120Hz |
| 最大輝度 | 記載なし | 3,000 nits |
| 保護 | Gorilla Glass Ceramic 3 | Flex Titanium |

カバーとメインで ppi が約24違う。
この差はランチャーにとって実害がある（後述のウィジェット密度スケーリングを参照）。

### dp 換算 **要実測**

Android のデフォルト密度（`densityDpi`）は端末ごとにメーカーが決めるため、公開スペックからは確定できない。
ありうる値ごとに dp を出すと次のようになる。

| densityDpi | カバー (dp) | メイン横向き (dp) |
| --- | --- | --- |
| 420 (2.625x) | 475 × 751 | 933 × 704 |
| 450 (2.8125x) | 443 × 700 | 871 × 657 |
| 480 (3.0x) | 416 × 657 | 816 × 616 |

ここから、Window Size Class の判定が密度によって変わる。

- カバーは幅 416〜475dp で、どの密度でも **COMPACT 幅**（600dp未満）。ただし一般的なスマートフォン（360〜412dp）より確実に広い。
- メイン横向きは幅 816〜933dp で、**MEDIUM 幅と EXPANDED 幅（840dp以上）の境界をまたぐ**。420dpi なら EXPANDED、480dpi なら MEDIUM に落ちる。
- メインは縦横どちらの向きでも高さが 900dp に届かず、**EXPANDED 高さにはならない**。

デフォルト密度が Size Class の分岐を変えてしまうので、実測を最優先で行う。
ユーザーが表示サイズを変更した場合にも同じ問題が起きるため、Size Class に依存した分岐は避け、実寸 dp で自前に判定する設計にしておくほうが安全である。

`smallestScreenWidthDp` は、メインで 616〜704dp、カバーで 416〜475dp になる。
リソース修飾子で分けるなら `sw600dp` がメインとカバーの境界として機能する。

## Flex Mode と S Pen

Fold8 では **Flex Mode が廃止された**。
ヒンジ機構が中間角度で止まらず、開き始めると最後まで開く挙動に変わったためとされる。
Fold8 Ultra と Flip8 には Flex Mode が残っている。

ランチャー側では、半開き姿勢（`HALF_OPENED`）に対応した UI を用意する必要がない。
ただし Jetpack WindowManager が `FoldingFeature.State.HALF_OPENED` を報告しないと確定したわけではないので、実機で確認する（**要実測**）。
報告されうるなら、少なくとも落ちない程度の縮退動作は必要になる。

**S Pen は非対応**。
Flex Titanium 層（ヒンジと OLED のあいだに入るチタン合金フィルム）が EMR デジタイザと物理的に両立しないため、Fold8 と Fold8 Ultra のどちらも S Pen 入力を持たない。
S Pen 前提のホーム操作（手書き検索、ペンのホバー）は設計から外してよい。

## その他のハードウェア

- SoC：Snapdragon 8 Elite Gen 5 for Galaxy
- RAM：12GB / 16GB
- バッテリー：4,800mAh
- カメラ：50MP メイン + 50MP 超広角
- 重量：出典が201gと209gに割れている（**要実測**）

## ソフトウェア環境

- Android 17
- One UI 9（2026年7月22日リリース）
- OS メジャーアップデート7回保証

### Android 17 でランチャーに効く変更

**ウィジェットの密度スケーリング**。
ウィジェットのパディング、テキストサイズ、レイアウト属性が、アプリ本来のコンテキストとターゲットディスプレイの密度差に応じて自動でスケールされるようになった。
カバーとメインで ppi が異なる Fold8 では、この挙動をどう扱うかがウィジェットホスト実装の分かれ目になる。
自動スケールに任せるか、`AppWidgetHostView` 側で明示的に制御するかを、実機の見た目で決める。

**ランチャーアイコンからのバブル化**。
ランチャーアイコンを長押しすると任意のアプリをバブルとして起動できるようになった。
大画面ではタスクバー内の bubble bar がバブルを管理する。
自作ランチャーで長押しメニューを自前実装する場合、この導線を再現するかを決める必要がある。

**Handoff API**。
`Activity.setHandoffEnabled()` と `Activity.onHandoffActivityDataRequested()` により、近くのデバイスで継続できるアクティビティをアプリが宣言する。
ランチャーとタスクバーが、その候補を提示する側になることが想定されている。

**向きとリサイズ制限の無視**。
大画面では、アプリが宣言した向き・リサイズ制限をシステムが無視する挙動が入っている。
ランチャー自身がこの対象になるかどうかは、メイン画面の dp が閾値を超えるかに依存する（前述の Size Class 問題と連動する）。

**デスクトップウィンドウ**。
インタラクティブ PiP が入り、ピン留めしたウィンドウが常に最前面で操作可能なまま残る。
Fold8 でデスクトップモードを使うかは、実機での挙動を見てから判断する。

### One UI 9 の制約 **要検証**

サードパーティランチャーに対する Samsung 側の制限は、公開情報が古く、One UI 9 での状態を確定できていない。
以下は過去の経緯であり、実機で確認する必要がある。

- **タスクバー**：One UI 4.1.1 (Android 12L) の時点で、サードパーティランチャー使用時にタスクバーの設定項目がグレーアウトされていた。One UI 5.1 で Nova Launcher でも使えるようになったという報告がある。Fold8 のタスクバーは横長メイン画面での多窓運用の中心なので、使えるかどうかで設計が大きく変わる。
- **ジェスチャーナビゲーション**：One UI 2.5 以降、サードパーティランチャーでも全画面ジェスチャーが使える。ただし「最近のアプリ」への遷移アニメーションは純正ランチャーと同等にはならない。Samsung は AOSP の QuickStep 相当の連携をサードパーティに開放していない。
- **エッジパネル、Now Bar、カバー画面のウィジェット**：純正ランチャーと結びついているものと、SystemUI 側にあるものの切り分けが未確認。

この節は実機確認の結果で全面的に書き換える前提とする。

### 現状の到達点（Good Lock / Home Up）

すでに Home Up でカスタムしている状態が出発点になる。
One UI 9 対応版（18.0.0.24 以降、2026年7月）で入った機能は次のとおり。

- 最大5本指のマルチフィンガージェスチャー。スワイプ、タップ、ピンチに最大36個のショートカット（アプリ、システム機能）を割り当てられる。
- ドック背景のカスタマイズ。ブラー、シャドウ、背景画像、色、形状、グリッドの独立スケーリング。
- エッジパネルの外観カスタマイズ。

自作ランチャーがこの水準を下回ると乗り換える意味がないので、これを最低ラインとして扱う。
差別化の余地は、Home Up が触れない領域、すなわちカバー画面とメイン画面で**別のホーム構造**を持つこと、および横長 4:3 を前提にしたレイアウトにある。
Home Up は One UI Home の設定を拡張するモジュールなので、One UI Home のページ・グリッド・ドックという構造そのものは変えられない。

## 実機で測る項目

以下は公開情報から確定できない。
`adb` で取得してこの文書に反映する。

```bash
# 論理ディスプレイの構成（折・開のそれぞれで実行して差分を見る）
adb shell dumpsys display | grep -E 'mDisplayId|uniqueId|density|DisplayDeviceInfo'
adb shell dumpsys window displays

# 現在のディスプレイのサイズと密度
adb shell wm size
adb shell wm density

# 端末姿勢（CLOSED / OPENED / HALF_OPENED が定義されているか）
adb shell cmd device_state print-states
adb shell cmd device_state state

# ホームアプリの候補とロール
adb shell cmd role get-role-holders android.app.role.HOME

# One UI のバージョンと折りたたみ関連 feature
adb shell getprop | grep -i -E 'oneui|fold'
adb shell pm list features | grep -i -E 'fold|hinge'
```

特に確定させたいのは次の三点である。

1. **カバーとメインが同一の論理ディスプレイか、別ディスプレイか**。同一なら折りたたみは設定変更（`onConfigurationChanged`）として届き、別ディスプレイならタスクの移動として届く。ランチャーの状態保持とアクティビティのライフサイクル設計がここで決まる。
2. **デフォルトの `densityDpi`**。前述のとおり Size Class の分岐が変わる。
3. **自然な向き（rotation 0）がどちらの画面基準か**。メインが横長なので、`Surface.ROTATION_0` がメインの横向きを指すのか、カバーの縦向きを指すのかで、ランチャーの向き固定の指定が変わる。

あわせて、Jetpack WindowManager の `WindowInfoTracker.windowLayoutInfo` から `FoldingFeature` を購読し、ヒンジの位置、`occlusionType`、`state` が何を返すかを記録する。

## 次にやること

1. 実機を接続して上記の実測項目を埋める。
2. サードパーティランチャー使用時の One UI 9 の制約（タスクバー、最近のアプリ、エッジパネル）を、Nova など既存ランチャーを一時的に既定にして確認する。
3. 確定した dp をもとに、カバーとメインそれぞれのホーム構造（グリッド、ページ、ドック）の設計に入る。

## 出典

- [Samsung Galaxy Z Fold8 - GSMArena](https://www.gsmarena.com/samsung_galaxy_z_fold_wide_5g-14673.php)
- [Galaxy Z Fold 8 debuts with wider display to fight the iPhone Ultra - Android Authority](https://www.androidauthority.com/samsung-galaxy-z-fold-8-3688636/)
- [Galaxy Unpacked July 2026: A First Look at Galaxy Z Fold8 Ultra, Galaxy Z Fold8 and Galaxy Z Flip8 - Samsung Newsroom](https://news.samsung.com/global/galaxy-unpacked-july-2026-a-first-look-at-galaxy-z-fold8-ultra-galaxy-z-fold8-and-galaxy-z-flip8)
- [Samsung Galaxy Z Fold 8 - Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Z_Fold_8)
- [Samsung's Galaxy Z Fold 8 is missing a fan-favorite foldable feature - Android Authority](https://www.androidauthority.com/galaxy-z-fold-8-drops-flex-mode-3690348/)
- [No S Pen on Galaxy Z Fold 8: The Flex Titanium Reason Explained - Benks](https://www.benks.com/blogs/benks-blog/galaxy-z-fold-8-no-s-pen-flex-titanium-reason)
- [Galaxy Z Fold 8 Ultra Screen Size: Leaked Display Specs Explained - Gadget Hacks](https://samsung.gadgethacks.com/news/galaxy-z-fold-8-ultra-screen-size-leaked-display-specs-explained/)
- [Features and APIs - Android 17 - Android Developers](https://developer.android.com/about/versions/17/features)
- [Android 17 is Here - Android Developers Blog](https://android-developers.googleblog.com/2026/06/Android-17.html)
- [Good Lock's Home Up update with new features for One UI 9.0 is out now - SamMobile](https://www.sammobile.com/news/good-lock-home-up-update-released-new-features-one-ui-9-0/)
- [Samsung Drops Big Good Lock Update for One UI 9.0 - Android Headlines](https://www.androidheadlines.com/2026/07/samsung-good-lock-home-up-one-ui-9-custom-gestures.html)
- [Samsung (mostly) disables Android 12L's taskbar when you use a third-party launcher - 9to5Google](https://9to5google.com/2022/09/11/samsung-android-taskbar-launcher/)
- [Samsung One UI 2.5 now lets you use Android 10 gestures w/ third-party launchers - 9to5Google](https://9to5google.com/2020/08/19/samsung-one-ui-2-5-now-lets-you-use-android-10-gestures-w-third-party-launchers/)
- [App continuity and Multi-tasking - Samsung Developer](https://developer.samsung.com/one-ui/foldable-and-largescreen/app-cont-and-multi.html)
