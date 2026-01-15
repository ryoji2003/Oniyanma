#　要点
**このサイクルの繰り返し**
1. 最新化: git checkout main → git pull origin main
2. 作業開始: git checkout -b feature/新しい機能名
3. 作業: コードを書く
4. 保存: git add . → git commit -m "メッセージ"
5. 送信: git push -u origin feature/新しい機能名
6. 合流: GitHubで Pull Request → Merge
**(1に戻る)**

# GitHubにあげないファイルの設定方法
.gitignoreファイルを作成し、除外ルールを書き込む
echo ".venv/" >> .gitignore
echo ".env" >> .gitignore
echo "__pycache__/" >> .gitignore
echo ".DS_Store" >> .gitignore


# git
## Directory作成
mkdir "gitで管理したDirectory名"
## gitの初期化
git init
## README.md作成
echo "Hello world" > README.md
## commt
git add "変更したfilename"
git commit -m "commit message"
## branchの作成
git branch -m main

# GitHubとの連携
この作業は、このプロジェクトで最初の一回だけ行えばOK。
## Step 1: GitHub上で「箱」を作る
まずはGitHubのWebサイト側で、アップロード先となる場所（リモートリポジトリ）を作成。
ブラウザで GitHub にログインする。

右上の「＋」アイコンから 「New repository」 をクリック。
以下の通りに入力。
1. Repository name: (Directory名と同じにする)

2. Public/Private: どちらでもOK。

3. Initialize this repository with: 何もチェックを入れない！
''注意: すでに手元で README.md を作っているので、ここでチェックを入れると競合の原因になる。''

4. 「Create repository」 ボタンを押す。

## Step 2: PCとGitHubを繋ぐコマンドを入力する
リポジトリを作成すると、画面にコマンドが表示される。
その中にある「…or push an existing repository from the command line」という部分を使う。

ターミナルで以下のコマンドを順番に入力。 (※ [あなたのユーザー名] の部分は、GitHubのユーザー名に置き換える)

1. 宛先（GitHubのURL）を登録する
git remote add origin https://github.com/[あなたのユーザー名]/[RepositoryName].git

2. 最初のファイルをアップロードする
git push -u origin main
''解説:''
git remote add origin URL: 「このURLを origin（オリジン）という名前で登録するよ」という命令。
git push -u origin main: 手元の main の内容を、origin（GitHub）に送信する。最初の1回だけ -u をつけると、次から単に git push と打つだけで送信できるようになる。

この git push が成功すれば、GitHubのページをリロードすると、あなたが作った README.md が表示されているはず。

#　Pull Request
## 作業用branchの作成
git checkout -b "branch name"
-"git checkout"で今いるbranchから違うbranchに移動する。
-"-b"は新しいbranchを作成するという意味。
### 作業用branchの削除
git branch -d "branch name"

### Branch Nameについて
**基本ルール： カテゴリ/内容**
実務では、**「スラッシュ(/)」**を使ってカテゴリ分けするのが一般的です。

1. 新しい機能を作る場合： feature/
-feature/add-login （ログイン機能の追加）
-feature/user-profile （プロフィール画面作成）
-feature/update-readme （今回のケースならこれ！）

2. バグを直す場合： fix/ または bugfix/
-fix/header-layout （ヘッダーの崩れ修正）
-fix/login-error （ログインエラーの解消）

3. ドキュメントのみ修正する場合： docs/
-docs/update-readme （READMEの更新）
-docs/api-guide （API仕様書の修正）

4. その他（整理・設定など）： chore/
chore/update-packages （ライブラリのバージョンアップなど、コードの動作に関わらない雑務）

**おすすめの書き方（まとめ）**
-英語で書く（日本語は避ける）
-小文字を使う
-単語の間はハイフン(-)
-カテゴリをスラッシュ(/)で区切る

##　変更を保存してGitHubに送る
**main**brachではなく、作業用branchに送る
git add "変更したfile"
git commit -m "commit message"
git push -u origin "作業用branch"


## Pull Requestを作成して合流
Web pageでGitHubを開く。
"**Compare & pull request**"ボタンを押す
### Title
一目で何をしたのかをわかるように記入する。
### Leave a comment(説明文)
-ここは詳細に書く。
-Markdown記法が使える。
### 入力が終わったら
1. 右下の"**Create pull request**"ボタンを押す。
2. "**Merge pull request**"ボタンを押す。
3. "**Confirm merge**"ボタンを押す。


# Repository　Nameについて
-全て小文字を使用する。
-単語の区切りには'-'を使用する。

# Token Nameについて
-どの端末で何用に使用するかをわかる名前にする。

