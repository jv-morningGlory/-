# Git 基础概念与配置

## 1. Git 工作区域

```mermaid
graph LR
    WD["📂 工作目录<br/>Working Directory"] -->|git add| SA["📋 暂存区<br/>Staging Area"]
    SA -->|git commit| LR["📦 本地仓库<br/>Local Repository"]
    LR -->|git push| RR["☁️ 远程仓库<br/>Remote"]
    SA -.->|"git restore --staged"| WD
    LR -.->|git reset HEAD| SA
    RR -.->|git pull / fetch| LR
```

| 方向 | 命令 | 说明 |
|------|------|------|
| 工作目录 → 暂存区 | `git add` | 将修改加入暂存区 |
| 暂存区 → 本地仓库 | `git commit` | 将暂存内容提交到本地仓库 |
| 本地仓库 → 远程仓库 | `git push` | 将本地提交推送到远程 |
| 暂存区 → 工作目录 | `git restore --staged` | 取消暂存（回退到工作目录） |
| 本地仓库 → 暂存区 | `git reset HEAD` | 撤销最近一次提交，改动回到暂存区 |
| 远程仓库 → 本地仓库 | `git pull / git fetch` | 拉取远程更新 |

## 2. git status 文件状态

| 状态 | 含义 |
|------|------|
| `Changes to be committed` | 已暂存（`git add` 后），等待 `git commit` |
| `Changes not staged for commit` | 已跟踪文件被修改，但未 `git add` |
| `Untracked files` | 未被 Git 管理的文件，Git 命令对其无效，**很容易丢失** |

## 3. SSH 密钥配置

本地生成一对公钥私钥，可配置到多个平台（GitHub、Gitee、GitLab）。

```bash
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"
```

参考：[Gitee SSH 配置指南](https://help.gitee.com/repository/ssh-key/generate-and-add-ssh-public-key)

## 4. 用户配置

```bash
# 全局配置
git config --global user.name "张三"
git config --global user.email "zhangsan@example.com"
git config --global --list

# 当前仓库配置（需进入仓库目录）
git config --local --list
```

## 5. 基本命令速查

```bash
# 提交与同步
git add --all                        # 所有改动加入暂存区
git commit -m "message"              # 提交到本地仓库
git push origin branch               # 推送到远程
git push --set-upstream origin xxx   # 首次推送并建立追踪

# 拉取
git fetch                            # 从远程拉取，不自动合并
git pull                             # 拉取 + 自动合并（= fetch + merge）
git pull --rebase                    # 拉取 + rebase（历史更整洁）

# 查看历史
git log --oneline --graph --all      # 紧凑的提交历史图
git show <commit-hash>               # 查看某次提交的改动
git diff                             # 工作区 vs 暂存区
git diff --cached                    # 暂存区 vs 最新提交

# 分支操作
git checkout -b feature/xxx          # 创建并切换分支
git branch -d feature/xxx            # 安全删除（已合并才能删）
git branch -D feature/xxx            # 强制删除
git branch -m old-name new-name      # 重命名分支

# 标签
git tag 1.0.0                        # 在当前 commit 打 tag
git push origin --tags               # 推送所有标签到远程

# cherry-pick
git cherry-pick <commit-hash>        # 将某个提交应用到当前分支
```
