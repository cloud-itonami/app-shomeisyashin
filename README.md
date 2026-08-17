# app-shomeisyashin

**証明写真（しょうめいしゃしん）—— 自撮り写真をパスポートや履歴書の規格に変換する
サービスの、設計文書と UI スカフォールドの保管物である。** 名前は「証明写真」の
ローマ字表記で、それ以外の手掛かりを名前は持たない。この節が名乗りである。

**保管されている 12 ファイルは出所とバイト単位で同一で、そのことは暗号学的に検査できる**
（§5）。出所 GitHub の実 tree とも一致している。

読む前に知っておくべきことが 1 つある。**`CLAUDE.md` が説明している機能は、この repo に
1 行も実装されていない。** ここに在るのは「Vite entry scaffold」と自称する 453 バイトの
Svelte スタブである（§2・§3）。

---

## 1. 在庫

`git ls-files` は 15 ファイル（2026-08-18 実測、この文書と `docs/` の 2 本を含む）。
3 層に分かれる。

| 層 | 数 | バイト | 何か |
|---|---|---|---|
| **保管対象**（出所からそのまま） | **12** | **7,567** | `CLAUDE.md` `NOTICE` と `appview/` 配下すべて |
| 抽出時の生成レコード | 2 | 可変 | `README.edn` / `migration.edn` |
| 後から足した文書（保管対象ではない） | 3 | 可変 | この `README.md` と `docs/` の 2 本 |

**意味のある数は第 1 層の 12 / 7,567 だけ**である。第 2 層と第 3 層はこの文書を
書き換えるたびに動く —— repo 全体のバイト総数をここに書かないのはそのためで、書いた
瞬間にこの文書自身がその数に含まれ、自己言及で必ず陳腐化する。

12 / 7,567 は `migration.edn` の `:source` に記録された値と一致する。**この一致は
検査できる**（§5）。

保管対象 12 ファイルの内訳は、**文書 2 つと、Svelte のスカフォールド 10 個**である:

| | ファイル | バイト |
|---|---|---|
| 文書 | `CLAUDE.md` | 2,060 |
| 文書 | `NOTICE` | 513 |
| 配備記述 | `appview/…/kotodama.jsonld` | 2,391 |
| スカフォールド | `svelte/` 配下 9 ファイル | 2,603 |

**サーバ・API・worker のソースは 1 ファイルも無い。** `.ts` は 3 つあるが、その内訳は
`main.ts`(122 B) / `svelte.d.ts`(136 B) / `vite.config.ts`(206 B) で、すべて足場である。

## 2. `CLAUDE.md` が説明している機能は入っていない

`CLAUDE.md` は次を表として記述している —— いずれも**この repo には無い**:

| `CLAUDE.md` の主張 | この repo の実物 |
|---|---|
| XRPC コマンド 4 種（`UploadPhoto` / `GenerateIDPhoto` / `ListPhotos` / `GetPhoto`） | 実装ファイルが**存在しない** |
| murakumo VL (qwen3-vl-8b) による顔検出・背景解析 | 呼び出し箇所 **0** |
| グラフノード 2 種（`:Photo` / `:IDPhoto`） | スキーマ定義も SQL も**無い** |
| 証明写真フォーマット 6 種（`passport_jp` 35x45 ほか） | 表は `CLAUDE.md` の散文に 6 行あるだけで、実装は**無い** |
| `component.wasm`（`kotodama.jsonld` の `component.path`） | **ファイルが無い** |

UI として実在するのは `svelte/src/App.svelte`（453 B）だけで、その中身は次で全部である:

```
etzhayyim-wasm-shomeisyashin-f901c7i4
Vite entry scaffold after SvelteKit cleanup.
```

**このファイル自身が「スカフォールド」と名乗っている。** 証明写真に関わる要素は
1 つも無い。

## 3. ビルドはできる。ただしこの repo 単体ではできない

`package.json` は `"@etzhayyim/design-system": "workspace:*"` を要求するが、**この repo に
pnpm workspace のルートが無い**（`pnpm-workspace.yaml` も lockfile も tracked file に無い）。
そのまま `pnpm install` すると exit 1 で落ちる（2026-08-18 実測）:

```
ERR_PNPM_WORKSPACE_PKG_NOT_FOUND  "@etzhayyim/design-system@workspace:*" is in the
dependencies but no package named "@etzhayyim/design-system" is present in the workspace
```

**不足している依存は実在する。** `@etzhayyim/design-system` は
**`kotoba-lang/svelte-design-system`**（`package.json` の `name` が一致、v0.1.0）である。
これを同じ workspace に置き、**design-system を先にビルド**すれば app は建つ
（2026-08-18 実測、exit 0）:

```
✓ 135 modules transformed.
dist/index.html                 0.42 kB
dist/assets/index-C-zwCK5o.css  0.24 kB
dist/assets/index-CjVA8Rgd.js   2.68 kB
✓ built in 690ms
```

**成果物が 2.68 kB であることが、この repo の中身を最もよく表している。** 手順は
`docs/operator-quickstart.md` §4。

## 4. 記録と実物の食い違い（保管対象なので直していない）

2026-08-18 実測。**どれも保管対象ファイルの中に在るので、直すと §5 の custody 検査が
落ちる。** ここでは可視化に留める。

| 場所 | 記録 | 実際 |
|---|---|---|
| `CLAUDE.md:22` | コンポーネントは `wasm/etzhayyim-wasm-shomeisyashin-f901c7i4/` | ディレクトリは **`appview/`**。`wasm/` は存在しない |
| `CLAUDE.md:53` | `cd 60-apps/etzhayyim-project-shomeisyashin/wasm/…` | 抽出前の monorepo のパス。この repo に無い |
| `kotodama.jsonld:5` | `component.path: "component.wasm"` | **ファイルが無い** |
| `kotodama.jsonld:83` | `staticDir: "/wasm/svelte/dist"` | `appview/` と噛み合わない |
| `kotodama.jsonld:3` | `@id: did:web:`**`syo`**`meisyashin.etzhayyim.com` | `name`/`project` は **`sho`**`meisyashin`。綴りが 2 通りあり、`routes` は両方を挙げている |
| `svelte/tailwind.config.js` | `content` に `…/packages/ts/design-system/dist/**` を **2 回** | 抽出前のパス。この repo からは解決しない（重複も無意味） |
| `package.json` | `vite: ^6.4.2` | `@sveltejs/vite-plugin-svelte@4.0.4` の peer は `vite@^5.0.0`。ビルドは通るが宣言は噛み合っていない |

### 宣言されているホストは存在しない

2026-08-18 実測（`host` / `curl`）:

| 名前 | 結果 |
|---|---|
| `shomeisyashin.etzhayyim.com` | **NXDOMAIN** |
| `syomeisyashin.etzhayyim.com` | **NXDOMAIN** |
| `f901c7i4.etzhayyim.com`（`CLAUDE.md` の smoke URL） | **NXDOMAIN** |
| `etzhayyim.com` | 解決する（`172.67.179.128`） |
| `https://etzhayyim.com/ns/kotodama/v1`（JSON-LD の `@context`） | **404** |

つまり **`https://shomeisyashin.etzhayyim.com` は動いていない。** この repo は稼働中
サービスのソースではなく、かつて配備を意図された設定と、その UI スカフォールドの
保管物である。

## 5. 保管されていること自体は検査できる

上記は全部「中身の話」だが、**この repo が出所を正しく保管しているか**は暗号学的に
確かめられる。`migration.edn` は出所の git tree SHA を記録している:

```
etzhayyim/root@c9e7df4b :  60-apps/etzhayyim-project-shomeisyashin
tree                    :  8936a6df448e6a6d7c4cb5a8e997bcecdfa9002b
```

`:identity :allowed-additions` に挙がっている追加物を除いてルート tree を再構成すると
この SHA になる。バイト総数の一致ではなく**ハッシュ**で見るので、足し引きが相殺する
改変も捕まる。

```bash
nbb docs/verify-custody.cljs            # ローカルのみ
nbb docs/verify-custody.cljs --origin   # 出所 GitHub の実 tree とも突き合わせる
```

実測は 4 件すべて ok（exit 0）で、**出所 GitHub の実 tree とも一致する** ——
`etzhayyim/root@c9e7df4b:60-apps/etzhayyim-project-shomeisyashin` が同じ
`8936a6df…` を返す。全文は `docs/operator-quickstart.md` §2。

**この検査は落ちることを確認してある。** 保管対象に 1 バイト足すと exit 1 になり、
`got 7568 / want 7567` と言って止まる（quickstart §6 に実測）。落ちない検査は劇場である。

## 6. 出所と、この文書が足したもの

出所は `etzhayyim/root`（`c9e7df4b`、`60-apps/etzhayyim-project-shomeisyashin`）。
ライセンスと charter rider は `NOTICE` を参照。

**`migration.edn` の `:identity :allowed-additions` は、この文書を足したときに
3 エントリ増やした**（`README.md` / `docs/operator-quickstart.md` /
`docs/verify-custody.cljs`）。これは記録を現実に合わせるための更新で、custody の錨で
ある `:source` ブロック（`:revision` / `:tree` / `:tracked-files` / `:bytes`）は
1 バイトも触っていない —— そちらを触れば §5 の検査が落ちる。

保管対象の 12 ファイルは、この文書を書く過程で 1 つも編集していない。だから §4 の
食い違いは**直していない**。
