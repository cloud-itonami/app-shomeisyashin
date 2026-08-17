#!/usr/bin/env nbb
;; verify-custody.cljs — app-shomeisyashin の保管検査
;;
;; この repo は etzhayyim/root からの抽出物である。抽出時に足された記録
;; （migration.edn の :identity :allowed-additions）を除いた残りは、出所の
;; tree と **バイト単位で同一** でなければならない。
;;
;; 検査はハッシュで行う。バイト総数の一致だけでは、足し引きが相殺する改変を
;; 通してしまう。git の tree オブジェクトを再構成して migration.edn の
;; :source :tree と突き合わせる。
;;
;;   nbb docs/verify-custody.cljs            # ローカルのみ（network 不要）
;;   nbb docs/verify-custody.cljs --origin   # 出所 GitHub の実 tree とも突き合わせる
;;
;; exit 0 = PASS / 1 = FAIL / 3 = 判定できなかった（0 でも 1 でもない）
;;
;; ⚠ 「測れなかった」を「問題なし」と同じ値で返さないこと。3 はそのための値で、
;;   git が無い・repo でない・記録が読めない・対象が 0 件、はすべて 3 で終わる。
;;
;; ⚠ tree の検査は HEAD を、バイト数の検査は working tree を見る。したがって
;;   tracked file を編集して commit していない状態は「tree ok / バイト FAIL」に
;;   なる。これは仕様で、未 commit の改変も捕まえるためにこうしてある。
;;
;; 出所: この検査器は cloud-itonami/app-roukisho・app-saiban の docs/verify-custody.cljs と
;; 同じものである。migration.edn の形（:source :tree / :tracked-files / :bytes と
;; :identity :allowed-additions）が抽出 repo 全体で共通なので、検査に repo 固有の
;; 定数が 1 つも要らない。**この段落は複製であることの宣言であって、言い訳ではない**
;; —— 片方だけ直る状態を避けたければ、直すときは両方を見ること。

(require '["node:child_process" :as cp]
         '["node:fs" :as fs]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def ^:private argv (vec (drop 2 (js->clj js/process.argv))))
(def ^:private check-origin? (some #{"--origin"} argv))

(defn- die! [code & msg]
  (binding [*print-fn* *print-err-fn*] (apply println msg))
  (js/process.exit code))

(defn- git [& args]
  (try
    (str/trim (str (cp/execFileSync "git" (clj->js (vec args)) #js {:encoding "utf8"})))
    (catch :default e
      (die! 3 "UNDETERMINED: git" (str/join " " args) "が失敗した —"
            (or (some-> e .-message) "(理由不明)")))))

;; ── 記録を読む ────────────────────────────────────────────────────────────
;;
;; ファイル全体を [ ] で包む。包まないと edn/read-string は **先頭の 1 フォーム
;; しか読まず残りを黙って捨てる** ので、末尾に壊れたテキストを足しても素通りする。

(def ^:private record
  (let [path "migration.edn"]
    (when-not (fs/existsSync path)
      (die! 3 "UNDETERMINED:" path "が無い。この repo のルートで実行すること"))
    (let [forms (try (edn/read-string (str "[" (fs/readFileSync path "utf8") "]"))
                     (catch :default e
                       (die! 3 "UNDETERMINED:" path "が EDN として読めない —"
                             (or (some-> e .-message) ""))))]
      (when-not (= 1 (count forms))
        (die! 1 (str "FAIL: " path " のトップレベルのフォームが " (count forms)
                     " 個ある（1 個であるべき）")))
      (first forms))))

(def ^:private expected-tree  (get-in record [:source :tree]))
(def ^:private expected-files (get-in record [:source :tracked-files]))
(def ^:private expected-bytes (get-in record [:source :bytes]))
(def ^:private additions      (get-in record [:identity :allowed-additions]))

(doseq [[k v] {":source :tree" expected-tree
               ":source :tracked-files" expected-files
               ":source :bytes" expected-bytes
               ":identity :allowed-additions" additions}]
  (when (nil? v) (die! 3 "UNDETERMINED: migration.edn に" k "が無い")))

;; 追加物が占めるルート直下の名前。`docs/x.md` は `docs` を占める。
;; 保管対象のパスをここに紛れ込ませて検査を迂回しようとしても、その分だけ
;; 再構成 tree から消えるのでハッシュが合わなくなる。
(def ^:private addition-roots
  (into #{} (map #(first (str/split % #"/")) additions)))

;; ── ① 出所 tree の再構成 ──────────────────────────────────────────────────

(def ^:private root-entries (str/split-lines (git "ls-tree" "HEAD")))

(when (< (count root-entries) 1)
  (die! 3 "UNDETERMINED: git ls-tree HEAD が空。commit の無い repo か"))

(def ^:private preserved-entries
  (remove (fn [line]
            (let [nm (last (str/split line #"\t" 2))]
              (contains? addition-roots nm)))
          root-entries))

(when (zero? (count preserved-entries))
  (die! 3 "UNDETERMINED: 保管対象のルートエントリが 0 件。"
        ":allowed-additions が全てを覆っている"))

(def ^:private actual-tree
  (try
    (str/trim (str (cp/execFileSync "git" #js ["mktree"]
                                    #js {:encoding "utf8"
                                         :input (str (str/join "\n" preserved-entries) "\n")})))
    (catch :default e
      (die! 3 "UNDETERMINED: git mktree が失敗した —" (or (some-> e .-message) "")))))

;; ── ② 保管対象の実ファイル数とバイト数 ────────────────────────────────────

(def ^:private preserved-files
  (remove (fn [p] (contains? addition-roots (first (str/split p #"/"))))
          (remove str/blank? (str/split-lines (git "ls-files")))))

(when (zero? (count preserved-files))
  (die! 3 "UNDETERMINED: 保管対象のファイルが 0 件"))

(def ^:private actual-bytes
  (reduce + 0 (map (fn [p]
                     (if (fs/existsSync p)
                       (.-size (fs/statSync p))
                       (die! 3 "UNDETERMINED: tracked なのに実体が無い:" p)))
                   preserved-files)))

;; ── 判定 ──────────────────────────────────────────────────────────────────

(def ^:private results
  (cond-> [{:what "出所 tree（再構成 vs 記録）" :ok (= actual-tree expected-tree)
            :got actual-tree :want expected-tree}
           {:what "保管ファイル数" :ok (= (count preserved-files) expected-files)
            :got (count preserved-files) :want expected-files}
           {:what "保管バイト数" :ok (= actual-bytes expected-bytes)
            :got actual-bytes :want expected-bytes}]
    check-origin?
    (conj (let [{:keys [repository revision path]} (:source record)]
            (if (some nil? [repository revision path])
              (die! 3 "UNDETERMINED: --origin には :source の :repository :revision :path が要る")
              (let [parent (str/join "/" (butlast (str/split path #"/")))
                    leaf   (last (str/split path #"/"))
                    out    (try
                             (str (cp/execFileSync
                                   "gh" (clj->js ["api" (str "repos/" repository "/contents/"
                                                             parent "?ref=" revision)
                                                  "--jq" (str ".[] | select(.name==\"" leaf "\") | .sha")])
                                   #js {:encoding "utf8"}))
                             (catch :default e
                               (die! 3 "UNDETERMINED: 出所 GitHub を引けなかった（gh 未認証/圏外?）—"
                                     (or (some-> e .-message) ""))))
                    sha (str/trim out)]
                (when (str/blank? sha)
                  (die! 3 "UNDETERMINED: 出所に" path "が見つからない"))
                {:what (str "出所 GitHub の実 tree（" repository "@" (subs revision 0 8) ":" path "）")
                 :ok (= sha expected-tree) :got sha :want expected-tree}))))))

(println (str "SCANNED\t" (count preserved-files) " 保管ファイル / "
              (count results) " 検査"))
(doseq [{:keys [what ok got want]} results]
  (println (str (if ok "  ok   " "  FAIL ") what))
  (println (str "         got  " got))
  (when-not ok (println (str "         want " want))))

(if (every? :ok results)
  (do (println (str "PASS — 保管対象 " (count preserved-files) " ファイルは出所と同一"
                    (when-not check-origin? "（--origin を付けると出所 GitHub とも突き合わせる）")))
      (js/process.exit 0))
  (do (println "FAIL — 保管対象が出所と一致しない")
      (js/process.exit 1)))
