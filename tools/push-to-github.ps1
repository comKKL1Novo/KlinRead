# 创建 GitHub 远程仓库并推送。
#
# 前置条件：先执行 gh auth login 完成登录。
#
# 用法：
#   pwsh tools/push-to-github.ps1              # 建成公开仓库
#   pwsh tools/push-to-github.ps1 -Private     # 建成私有仓库

param(
    [switch]$Private,
    [string]$RepoName = 'KlinRead'
)

$ErrorActionPreference = 'Continue'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Fail($msg) {
    Write-Host ""
    Write-Host "✗ $msg" -ForegroundColor Red
    exit 1
}

# ── 前置检查 ────────────────────────────────────────────────────────────────

Write-Host "[1/4] 检查登录状态..." -ForegroundColor Cyan
$authOut = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host $authOut
    Fail "尚未登录 GitHub。请先执行：  gh auth login"
}

$user = (gh api user --jq '.login' 2>&1).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($user)) {
    Fail "无法读取 GitHub 用户名，请重新执行 gh auth login"
}
Write-Host "      已登录：$user"

Write-Host ""
Write-Host "[2/4] 检查本地仓库..." -ForegroundColor Cyan

if (-not (Test-Path (Join-Path $root '.git'))) {
    Fail "当前目录不是 git 仓库：$root"
}

$branch = (git branch --show-current).Trim()
if ($branch -ne 'main') {
    Write-Host "      当前分支是 $branch，将重命名为 main"
    git branch -M main
    $branch = 'main'
}
Write-Host "      分支：$branch"

$dirty = git status --porcelain
if ($dirty) {
    Write-Host "      警告：工作区有未提交的改动" -ForegroundColor Yellow
    $dirty | ForEach-Object { Write-Host "        $_" }
    Fail "请先提交或撤销这些改动再推送"
}
Write-Host "      工作区干净"

$commits = (git rev-list --count HEAD).Trim()
Write-Host "      提交数：$commits"

# 关键检查：gradlew 必须有可执行位，否则 Linux CI 会失败
$mode = (git ls-files -s gradlew).Split(' ')[0]
if ($mode -ne '100755') {
    Fail "gradlew 缺少可执行位（当前 $mode）。修复：git update-index --chmod=+x gradlew"
}
Write-Host "      gradlew 可执行位正常"

# ── 检查远程仓库是否已存在 ──────────────────────────────────────────────────

Write-Host ""
Write-Host "[3/4] 检查远程仓库..." -ForegroundColor Cyan

$existing = gh repo view "$user/$RepoName" --json name 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "      远程仓库 $user/$RepoName 已存在，将直接推送" -ForegroundColor Yellow
    $hasRemote = (git remote) -contains 'origin'
    if (-not $hasRemote) {
        git remote add origin "https://github.com/$user/$RepoName.git"
        Write-Host "      已添加 origin"
    }
    Write-Host ""
    Write-Host "[4/4] 推送..." -ForegroundColor Cyan
    git push -u origin main
} else {
    $visibility = if ($Private) { '--private' } else { '--public' }
    Write-Host "      将创建 $visibility 仓库：$user/$RepoName"
    Write-Host ""
    Write-Host "[4/4] 创建并推送..." -ForegroundColor Cyan
    gh repo create $RepoName --source=. --remote=origin --push $visibility `
        --description "一个界面简洁的本地电子书阅读器，Kotlin + Jetpack Compose，多格式解析全部手写"
}

if ($LASTEXITCODE -ne 0) {
    Fail "推送失败，请查看上面的输出"
}

# ── 结果 ────────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "✓ 完成" -ForegroundColor Green
Write-Host ""
Write-Host "仓库地址： https://github.com/$user/$RepoName"

$remoteUrl = (git remote get-url origin 2>$null)
Write-Host "origin  ： $remoteUrl"
Write-Host ""
Write-Host "CI 会在后台自动跑第一次构建，查看进度："
Write-Host "  gh run list --limit 5"
Write-Host "  gh run watch"
