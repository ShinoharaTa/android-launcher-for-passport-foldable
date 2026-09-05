# 他のランチャーの操作慣習と、ここで採るもの

実機で触った結果、ナビゲーションと経路をもう一段整理する必要が分かった(2026-09-05)。
独自の構造(ウィジェット面とアプリのページ、カバーではめくり、開くと並ぶ)は保ちつつ、操作そのものは多くの人が体で覚えている慣習に寄せる。
そのために主要なランチャーの操作を調べ、共通しているものを採り、独自性が価値になっていないものは捨てる。

## 調べた範囲

- Android 純正: Pixel Launcher(Android 16)、AOSP Launcher3
- Samsung: One UI 8 Home と Good Lock の Home Up
- 定番のサードパーティ: Nova Launcher、Lawnchair、Smart Launcher 6、Action Launcher
- 検索や一覧を主役にするもの: Niagara Launcher、Kvaesitso
- 比較対象として iOS 26 のホーム画面

## 慣習の一覧

| 操作 | Pixel / Launcher3 | One UI 8 | Nova / Lawnchair | Niagara / Kvaesitso | iOS 26 |
| --- | --- | --- | --- | --- | --- |
| 上スワイプ | 全アプリ。指に追従 | 全アプリ | 全アプリ(既定または設定) | 一覧そのものがホーム | App Library は最終ページの先 |
| 下スワイプ | 通知パネル | Finder(Home Up で復活) | 検索か通知を選べる | 検索(Kvaesitso の主経路) | Spotlight |
| 空き領域の長押し | 壁紙、ウィジェット、設定 | 壁紙、ウィジェット、設定 | 同じ | 設定 | 編集モード(揺れる) |
| ウィジェットの追加 | 長押しメニューから | 長押しメニューから | 同じ | 同じ | 長押しメニューから |
| アプリをアプリに落とす | 少し留めると輪が出てフォルダ | 枠が出たら離すとフォルダ | 同じ | 一覧なので無い | 少し留めるとフォルダ |
| 占有セルに落とす | 押しのけて並べ替える | 押しのける | 押しのける | 一覧の順序が変わる | 押しのける |
| 中身が 1 個のフォルダ | 解けてアプリに戻る | 残る | 設定次第 | 無し | 残る |
| 一覧の探し方 | 上端の検索欄、頭文字 | Finder、頭文字 | 検索欄、頭文字 | 端の頭文字バー | Spotlight |
| 折りたたみ端末 | 画面ごとに列数が変わる | カバーとメインで別レイアウト | 幅で列数が変わる | 1 レイアウトを共有 | 該当なし |

読み取れることは三つある。

- **上スワイプで全アプリ、下スワイプで検索、空き領域の長押しで編集** は、Android でも iOS でもほぼ共通で、ボタンやタブを介さない。
- **アプリを重ねてフォルダにする操作は、留める時間と目に見える枠で「わざと」を区別している**。留めずに離せば押しのけになる。
- **ウィジェットの追加は長押しメニューの仕事** で、アプリ一覧の中には無い。

## ここで採るもの

### 入口を三つのジェスチャに揃える

- **上スワイプ → 全アプリ**。スクロールしないアプリのページではどこからでも、ウィジェット面ではドックから。指に追従する既存の仕組みをそのまま使う。ドック右端のボタンは外す。
- **下スワイプ → 検索(Finder)**。同じ一覧を、検索欄に焦点を当てた状態で出す。別の面は作らない。ウィジェット面では先頭まで戻した状態で下に引いたとき。
- **空き領域の長押し → メニュー**。ウィジェットの追加と設定はここから。ドロワーの APPS / WIDGETS のタブは無くし、ドロワーは全アプリだけにする。

Samsung の Finder は「アプリ一覧の検索」であり、One UI でも上スワイプの一覧と同じ画面に着く。
ここでも一覧と検索を一つの面にし、入口だけを二つにする。

### ドラッグの規則を慣習に合わせる

- 空きセルに落とせば置く。
- 占有セルに落とせば **押しのける**。落とした先の要素が、空いている側へ一つずつずれる。ずらせないときは元に戻る。
- アプリをアプリの上に **0.5 秒留めると輪が出る**。輪が出ている間に離すとフォルダになる。留めずに離せば押しのけになる。フォルダの上でも同じで、留めると中に入り、留めなければ押しのける。
- 置けなかったときは、影が元の位置へ戻る動きと振動で伝える。
- 落ちる位置は、ドラッグ中に枠で示す。

いまの実装は、アプリの上に落とすと即座にフォルダになり、それ以外の占有セルは無言で拒否する。
これが「勝手にフォルダができる」「移動できない」という体験の原因である。

### フォルダは残す

- 中身が 1 個になっても解かない。フォルダを消すのはメニューの REMOVE だけ。
- フォルダの移動は中身ごと。中身の順序も保つ。
- フォルダを作る操作は、上の「留める」だけにする。メニューに「新しいフォルダ」は置かない。空のフォルダに意味が無いためである。

### ページと面は変えない

ウィジェット面 1 枚とアプリのページ何枚か、カバーではめくり、開くと並ぶ、という構造は保つ。
Niagara のように 1 レイアウトを共有する考え方と、One UI のように画面ごとに別レイアウトを持つ考え方の中間で、
同じ配置を画面の幅に応じて見せ方だけ変える。

## 採らないもの

- **下スワイプで通知パネル**(Pixel の既定)。通知はステータスバーからの下スワイプで足りる。ホームの下スワイプは検索に使う。
- **アイコンごとのスワイプ動作**(Nova、Smart Launcher)。覚える量が増え、誤動作の原因になる。
- **一覧だけのホーム**(Niagara)。ウィジェット面の価値と両立しない。
- **編集モード**(iOS の揺れる状態)。長押しからそのまま動かせるほうが操作が一つ少ない。
- **ドロワーのタブ**。ウィジェットの追加を一覧に混ぜる理由が無い。

## 出典

- [Samsung's Home Up to bring back Finder Swipe function after users demand - Sammy Fans](https://www.sammyfans.com/2024/03/26/samsungs-home-up-to-bring-back-finder-swipe-function-after-users-demand/)
- [Samsung One UI: How to Enable Swipe-Down Search On Galaxy Phone's Home Screen - samlover](https://samlover.com/2024/04/16/samsung-one-ui-how-to-enable-swipe-down-search-on-galaxy-phones-home-screen/)
- [Home Up Gets Major One UI 8 Update with Customization Upgrades - SammyGuru](https://sammyguru.com/home-up-gets-major-one-ui-8-update-with-customization-upgrades/)
- [Create App Folders on Galaxy Smartphones - Samsung](https://ushl.samsung.com/hk_en/support/mobile-devices/create-app-folders-on-galaxy-smartphones)
- [Pixel Launcher - 9to5Google](https://9to5google.com/guides/pixel-launcher/)
- [The 6 things we hate about the Pixel Launcher - Android Police](https://www.androidpolice.com/6-things-we-hate-about-the-pixel-launcher/)
- [Launcher3 - Android Open Source Project](https://android.googlesource.com/platform/packages/apps/Launcher3/+/42f3b9e/src/com/android/launcher3)
- [Nova Launcher FAQ](https://novalauncher.com/faq/)
- [Nova Launcher 101: How to Set Up Home Screen Gestures - Gadget Hacks](https://android.gadgethacks.com/how-to/nova-launcher-101-set-up-home-screen-gestures-0179904/)
- [How to Use Lawnchair Launcher on Android: Setup Guide](https://lawnchairlauncher.com/how-to-use-lawnchair-launcher-on-android/)
- [Gestures - Smart Launcher](https://docs.smartlauncher.net/faq/personalization/gestures)
- [Alphabet settings - Niagara Launcher Help](https://help.niagaralauncher.app/article/171-alphabet-settings)
- [Niagara Launcher Declutters Your Foldable Home Screen - WhistleOut](https://www.whistleout.com/CellPhones/Apps/niagara-launcher-app-for-foldable-phones-review)
- [This is the only launcher you need if you have too many apps on your phone - Android Police](https://www.androidpolice.com/only-launcher-you-need-if-you-have-too-many-apps-on-phone/)
- [Kvaesitso Launcher](https://kvaesitso.com/)
- [Search with Spotlight on iPhone - Apple Support](https://support.apple.com/guide/iphone/search-on-iphone-iph3c511548/ios)
- [Galaxy Z Fold 7 Drops Home and App Screen Grid Customization on Main Display - SammyGuru](https://sammyguru.com/galaxy-z-fold-7-drops-home-and-app-screen-grid-customization-on-main-display/)
