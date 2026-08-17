# app-shomeisyashin — operator quickstart

**この文書の手順は 2026-08-18 に端から端まで実際に走らせた。** 掲載している出力は
その実行で印字された値である。踏めなかった手順は「踏めない」と書いてある。

前提: `git` / `nbb` / `pnpm`（実測 10.26.2）/ `node`。

---

## 1. 取得

```bash
git clone https://github.com/cloud-itonami/app-shomeisyashin
cd app-shomeisyashin
```

`git ls-files` は 15 ファイル（この文書と `docs/` と `README.md` を含む）。
そのうち**保管対象は 12**（§2 で検査する）。

## 2. 保管されていることを検査する（network 不要）

この repo は `etzhayyim/root` からの抽出物なので、まず「出所と同一か」を確かめる。

```bash
nbb docs/verify-custody.cljs            # ローカルのみ
nbb docs/verify-custody.cljs --origin   # 出所 GitHub の実 tree とも突き合わせる
```

`--origin` まで含めた実測（exit 0）:

```
SCANNED	12 保管ファイル / 4 検査
  ok   出所 tree（再構成 vs 記録）
         got  8936a6df448e6a6d7c4cb5a8e997bcecdfa9002b
  ok   保管ファイル数
         got  12
  ok   保管バイト数
         got  7567
  ok   出所 GitHub の実 tree（etzhayyim/root@c9e7df4b:60-apps/etzhayyim-project-shomeisyashin）
         got  8936a6df448e6a6d7c4cb5a8e997bcecdfa9002b
PASS — 保管対象 12 ファイルは出所と同一
```

`--origin` を付けない場合は検査が 3 件になり、最後の 1 行が落ちるだけで結果は同じ。

検査は**バイト総数ではなく git tree のハッシュ**で行う。総数だけを見ると、足し引きが
相殺する改変を通してしまう。

**この検査は落ちることを確認してある**（§6）。

## 3. ビルドする —— この repo 単体では**できない**

`CLAUDE.md` の Build & Deploy 節はこう書いている:

```bash
cd 60-apps/etzhayyim-project-shomeisyashin/wasm/etzhayyim-wasm-shomeisyashin-f901c7i4/svelte
pnpm install && pnpm build
```

**このパスはこの repo に存在しない。** `60-apps/…` は抽出前の monorepo のパスで、
`wasm/` という階層も無い（実際は `appview/`）。この repo での対応する場所は:

```
appview/etzhayyim-wasm-shomeisyashin-f901c7i4/svelte
```

そこで `pnpm install` を実行すると **exit 1 で落ちる**（2026-08-18 実測）:

```
ERR_PNPM_WORKSPACE_PKG_NOT_FOUND  In : "@etzhayyim/design-system@workspace:*" is in the
dependencies but no package named "@etzhayyim/design-system" is present in the workspace
```

`package.json` が `"@etzhayyim/design-system": "workspace:*"` を要求しているが、
**この repo に pnpm workspace のルートが無い**（`pnpm-workspace.yaml` も lockfile も
tracked file に無い）。抽出によって、依存を供給していた側が repo の外に出た。

## 4. ビルドできる形に組み直す（実測: 通る）

不足している `@etzhayyim/design-system` は **`kotoba-lang/svelte-design-system`**
（`package.json` の `name` が `@etzhayyim/design-system`、version 0.1.0。2026-08-18 実測）。
これを同じ workspace に置けばビルドは通る。

```bash
W=/tmp/shomei-build
mkdir -p "$W/packages"
git clone https://github.com/kotoba-lang/svelte-design-system "$W/packages/design-system"
cp -R appview/etzhayyim-wasm-shomeisyashin-f901c7i4/svelte "$W/packages/app"
printf 'packages:\n  - "packages/*"\n' > "$W/pnpm-workspace.yaml"
printf '{"name":"shomei-build","private":true,"version":"0.0.0"}\n' > "$W/package.json"
cd "$W" && pnpm install
```

**design-system を先にビルドすること。** 順番を守らないと落ちる:

```bash
pnpm --filter ./packages/design-system build   # svelte-package: src/lib -> dist
pnpm --filter ./packages/app build
```

先に app を建てると、app の `tailwind.config.js` が design-system の**ビルド出力**を
import しているためここで止まる（実測）:

```
[vite:css] [postcss] Cannot find module
'…/node_modules/@etzhayyim/design-system/dist/plugin/tailwind.js'
```

順番どおりなら通る（実測、exit 0）:

```
vite v6.4.3 building for production...
✓ 135 modules transformed.
dist/index.html                 0.42 kB │ gzip: 0.29 kB
dist/assets/index-C-zwCK5o.css  0.24 kB │ gzip: 0.21 kB
dist/assets/index-CjVA8Rgd.js   2.68 kB │ gzip: 1.32 kB
✓ built in 690ms
```

**成果物が 2.68 kB であることが、この repo の中身を最もよく表している** —— 建つのは
`README.md` §3 のスカフォールドであって、証明写真の機能ではない。

このワークスペースでビルドを回すときは resource governor を通すこと
（superproject の規約。同時に 1 本だけ）:

```bash
node <superproject>/scripts/resource-guard.mjs run build -- pnpm --filter ./packages/app build
```

### 実測した警告 2 件（この repo の記録の食い違い）

| 警告 | 中身 |
|---|---|
| peer 不一致 | `@sveltejs/vite-plugin-svelte@4.0.4` は `vite@^5.0.0` を要求するが、`package.json` は `vite: ^6.4.2` を宣言（解決値 6.4.3）。ビルドは通るが宣言は噛み合っていない |
| 死んだ glob | `tailwind.config.js` の `content` が `../../../../../../packages/ts/design-system/dist/**` を**2 回**挙げている。抽出前の monorepo のパスで、この repo からは解決しない |

## 5. デプロイ —— 宛先が存在しない

`CLAUDE.md` の deploy 節は `--smoke-url https://f901c7i4.etzhayyim.com/health` を使う。
2026-08-18 実測（`host`）:

| 名前 | 結果 |
|---|---|
| `shomeisyashin.etzhayyim.com` | **NXDOMAIN** |
| `syomeisyashin.etzhayyim.com` | **NXDOMAIN** |
| `f901c7i4.etzhayyim.com` | **NXDOMAIN** |
| `etzhayyim.com` | 解決する（`172.67.179.128`） |

`kotodama.jsonld` が宣言する 3 ホストはいずれも存在しない。`@context` の
`https://etzhayyim.com/ns/kotodama/v1` も **404**。

さらに `kotodama.jsonld` の `component.path` は `component.wasm` を指すが、
**そのファイルは repo に無い**（tracked file 12 件のうちに存在しない）。
`triggers.http.staticDir` は `/wasm/svelte/dist` で、これも `appview/` と噛み合わない。

**したがって deploy 手順は実行できない。** この repo は稼働中サービスのソースではなく、
かつて配備を意図された設定と、その UI スカフォールドの保管物である。

## 6. 検査が落ちることを確認する

検査は「落ちること」を見て初めて検査になる。**保管対象を 1 バイト変えて再実行する:**

```bash
printf '\n' >> CLAUDE.md
nbb docs/verify-custody.cljs ; echo "exit=$?"
git checkout CLAUDE.md          # 戻す
```

実測（exit 1）:

```
  ok   出所 tree（再構成 vs 記録）
  ok   保管ファイル数            12
  FAIL 保管バイト数
         got  7568
         want 7567
FAIL — 保管対象が出所と一致しない
```

tree の検査は HEAD を、バイト数の検査は working tree を見るので、**未 commit の改変は
「tree ok / バイト FAIL」**として出る。これは仕様である。

commit まで済ませた改変は tree 側が落ちる。`:tree` と `:bytes` を新しい値に**揃えた**
偽造はローカル検査を通り抜けるので、その場合は `--origin` を使う（出所 GitHub の実 tree と
突き合わせるため、記録を書き換えても合わない）。

## 7. 変更するときに壊してはいけないもの

`migration.edn` の `:source` ブロック（`:revision` / `:tree` / `:tracked-files` /
`:bytes`）は custody の錨である。**触ると §2 が落ちる。**

文書を足すときは `:identity :allowed-additions` に足す。現在の許可リスト:

```clojure
["README.edn" "migration.edn" "README.md" "docs/operator-quickstart.md" "docs/verify-custody.cljs"]
```

保管対象の 12 ファイルは編集しない。`README.md` §4 が列挙した食い違い（`wasm/` パス・
死んだホスト・欠けている `component.wasm`）を**直していない**のはそのためで、直せば
custody 検査が落ちる。ここでは可視化に留めている。
