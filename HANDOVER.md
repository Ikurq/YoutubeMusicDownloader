# YoutubeMusicDownloader 開発引き継ぎ資料

本書は、個人用音楽ダウンロード＆プレイヤーアプリ「Music DL & Player（YoutubeMusicDownloader）」の開発を別のPC環境へ移行し、スムーズに継続するための開発引き継ぎ資料です。

---

## 1. プロジェクト基本情報

- **リポジトリURL**: `https://github.com/Ikurq/YoutubeMusicDownloader.git`
- **主要ブランチ**: `main`
- **現在のバージョン**: `v3.5.0`（GitHub ReleasesにてAPK配信対応済み）
- **対象プラットフォーム**: Android（Min SDK: 26 / Target SDK: 34 / Compile SDK: 34）
- **主要技術スタック**:
  - 言語: Kotlin
  - UIフレームワーク: Jetpack Compose (Material 3)
  - 非同期処理: Kotlin Coroutines & Flow (StateFlow)
  - アーキテクチャ: MVVM + Repository パターン
  - 再生エンジン: MediaPlayer + MediaSession (Foreground Service)
  - 画像読み込み: Coil (Compose)
  - ダウンロード・検索: YouTube InnerTube API (直接HTTP通信) / iTunes Search API / Gemini API

---

## 2. 新PCでの環境構築手順

### 必須要件
1. **Android Studio**: 最新の安定版（Ladybug / Jellyfish 以降推奨）
2. **JDK 21**: Android Studio同梱のJBR (JetBrains Runtime 21) を使用します。

> **Java実行環境の注意点**:
> 本プロジェクトのGradleビルドはJava 21で動作するように設定されています。システムの環境変数でJava 17や古いJavaが優先されているとビルド時にデーモンが停止したりエラーになるため、ターミナル実行時は明示的にAndroid Studio同梱のJBR 21を指定してください。

### ステップ1: リポジトリのクローン
```bash
git clone https://github.com/Ikurq/YoutubeMusicDownloader.git
cd YoutubeMusicDownloader
```

### ステップ2: Android Studioの設定
1. Android Studioで `YoutubeMusicDownloader` ディレクトリを開きます。
2. `Settings (Preferences)` > `Build, Execution, Deployment` > `Build Tools` > `Gradle` を開きます。
3. **Gradle JDK** を `Embedded JDK (JetBrains Runtime 21)` に設定します。
4. `Sync Project with Gradle Files` を実行し、依存関係のダウンロードとインデックス作成が完了するのを待ちます。

### ステップ3: ターミナルでのビルド確認 (PowerShell)
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat compileDebugKotlin
```
`BUILD SUCCESSFUL` と表示されれば環境構築は完了です。

---

## 3. ディレクトリ・アーキテクチャ構成

ソースコードルート: `app/src/main/java/com/example/youtubemusicdownloder/`

```
com.example.youtubemusicdownloder/
├── model/
│   ├── Track.kt                     # 楽曲データモデル（ローカルパス、メタデータ、お気に入り等）
│   ├── Playlist.kt                  # プレイリストデータモデル
│   └── RepeatMode.kt                # リピート設定列挙型 (OFF / ALL / ONE)
│
├── data/
│   ├── YoutubeSearchService.kt      # YouTube InnerTube検索、チャンネル詳細取得、関連動画API
│   ├── YoutubeDownloader.kt         # 音源マルチスレッドダウンロード & /Music/MusicPlayerDL/ への保存
│   ├── YoutubeExtractor.kt          # URL・ビデオID・サムネイル抽出
│   ├── ChannelInfo / SearchResult   # チャンネル情報・検索結果データクラス
│   ├── MetadataCleaner.kt           # タイトル・アーティスト名の表記ゆれ正規化
│   ├── GeminiMetadataCleaner.kt     # Gemini 1.5 Flashによる自動プレイリスト生成
│   ├── OfficialMusicResolver.kt     # iTunes Search APIを活用した公式アルバム・アートワーク照会
│   ├── LocalAudioImporter.kt        # 端末ストレージ内の既存音楽ファイルのインポート処理
│   ├── DownloadQueueManager.kt      # ダウンロードキュー管理（順次DL、リトライ、キャンセル）
│   ├── UpdateManager.kt             # GitHub Releases連携によるアプリアップデート確認・APK直接更新
│   └── MusicRepository.kt           # SharedPreferencesを用いたメタデータ・設定の永続化
│
├── media/
│   └── MusicPlayerManager.kt        # MediaPlayer制御、AudioFocus、BecomingNoisy、スリープタイマー、再生速度
│
├── service/
│   └── MusicPlaybackService.kt      # バックグラウンド再生Foreground Service & ロック画面/通知メディアコントロール
│
└── ui/
    ├── MusicViewModel.kt            # アプリ全体のStateFlow状態管理
    ├── screens/
    │   ├── MainScreen.kt            # メイン画面（タブナビゲーション、ミニプレイヤー、ダイアログ制御）
    │   ├── UrlSearchScreen.kt       # 楽曲検索、チャンネル詳細閲覧、YouTube URL直接ダウンロード
    │   ├── PlaylistsScreen.kt       # プレイリスト一覧・作成・Gemini AI自動生成
    │   ├── LibraryScreen.kt         # ダウンロード済み楽曲一覧、ソート、お気に入り絞り込み、端末曲追加
    │   └── PlayerSheet.kt           # フルスクリーン再生プレイヤー（シーク、速度変更、タイマー設定等）
    └── components/
        ├── ChannelCard.kt           # 検索ヒットした公式チャンネルのバナーカード
        ├── SearchResultRow.kt       # 検索結果行（サムネイル、タイトル、チャンネルリンク、保存ボタン）
        ├── MiniPlayer.kt            # 画面下部常駐ミニプレイヤー
        ├── DownloadQueueSheet.kt    # ダウンロードキュー進捗モーダル
        ├── ApiSettingsCard.kt       # Gemini APIキー設定カード
        └── UpdateDialog.kt          # 新バージョン通知 & インストールダイアログ
```

---

## 4. 最近実装・改善された重要機能

直近の開発で以下の改善・機能追加が完了し、`main` ブランチに反映されています。

1. **検索精度の刷新とチャンネル単位のブラウジング**:
   - YouTube InnerTube本来の高精度な関連度ランキングを維持（従来のTopic強制ソートによる意図しない結果の混入を撤廃）。
   - 検索結果に公式チャンネルバナーを表示し、タップでチャンネルの全アップロード動画・楽曲（30件以上）を表示。
   - チャンネル画面内でのリアルタイム絞り込み検索および一括ダウンロードキュー追加に対応。
   - 検索バーにチャンネルURL（`youtube.com/@ハンドル` や `youtube.com/channel/UC...`）を入力すると自動でチャンネルを開く仕様を追加。
2. **音楽プレイヤーとしての必須機能拡充**:
   - **Audio Focus**: 電話着信時の一時停止、通話終了後の自動再開。
   - **Becoming Noisy対応**: イヤホン抜去・Bluetooth切断時の自動一時停止（音漏れ防止）。
   - **スリープタイマー**: 15分〜120分の指定時間経過後に自動フェード停止。
   - **再生速度変更**: 0.5x〜2.0x（0.1刻み）でのピッチ保持再生速度切り替え。
   - **お気に入り機能**: 楽曲のお気に入り登録およびライブラリでの「お気に入り」ワンタップ絞り込み。
3. **バックグラウンド処理の最適化**:
   - 通知エリアのシークバー操作不整合の修正。
   - 楽曲削除時に端末ストレージ上の実オーディオファイルを確実に物理削除するファイル保護処理。
   - 500ms周期の過剰な通知更新によるバッテリー消費の解消。

---

## 5. 重要な開発規約・制約事項

**絵文字の使用禁止ポリシー**:
- **コミットメッセージ、アプリ内UI、トースト、ログ、AIプロンプト等で絵文字を使用することは原則禁止**となっています。
- UI上で装飾が必要な場合は、絵文字（✨、♥など）ではなく、必ず Material 3 のベクターアイコン（`Icons.Default.Favorite`、`Icons.Default.AutoAwesome` など）を使用してください。

### ストレージ保存場所
- 保存ディレクトリ: 外部ストレージの `/Music/MusicPlayerDL/` 配下。
- アプリ内から曲を削除した場合、物理ファイルも同時に安全に消去する実装が維持されています。

---

## 6. ビルド・リリース手順

### デバッグビルドの作成
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
生成先: `app/build/outputs/apk/debug/app-debug.apk`

### リリース手順（アプリ内自動更新連携）
本アプリにはGitHub Releases連携の自動更新機能（`UpdateManager.kt`）が組み込まれています。新バージョンをリリースする際の手順は以下の通りです。

1. `app/build.gradle.kts` の `versionCode` と `versionName` を更新（例: `3.5.0` -> `3.6.0`）。
2. `UpdateManager.kt` 内の `CURRENT_VERSION` を新しいバージョン名（例: `"3.6.0"`）に更新。
3. 変更をコミットしてプッシュ（**絵文字なし**のコミットメッセージ）:
   ```bash
   git commit -m "Bump version to v3.6.0 for release"
   git push origin main
   ```
4. APKをビルド:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
5. GitHub CLI (`gh`) またはブラウザでタグ作成とリリースの作成:
   ```powershell
   gh release create v3.6.0 app\build\outputs\apk\debug\app-debug.apk --title "v3.6.0" --notes "Release v3.6.0"
   ```
   アプリ起動時に自動で新バージョンが検出され、ユーザーが「今すぐアップデート」を押すだけで自動インストールされます。
