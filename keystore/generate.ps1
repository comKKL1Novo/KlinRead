# 生成本地 release 签名用的 keystore。
#
# 这个文件不入库（.gitignore 里的 *.jks），所以换一台机器就要重新生成一次。
# 重新生成后无法覆盖安装旧版本 —— Android 会以签名不一致为由拒绝，
# 必须先卸载旧版（会清掉应用数据）。
#
# 用法：
#   pwsh keystore\generate.ps1

$ErrorActionPreference = 'Stop'

$keytool = 'C:\Software\AndroidStudio\jbr\bin\keytool.exe'
$root = Split-Path -Parent $PSScriptRoot
$ks = Join-Path $PSScriptRoot 'phone-release.jks'

if (-not (Test-Path $keytool)) {
    Write-Host "找不到 keytool：$keytool" -ForegroundColor Red
    Write-Host '请修改脚本里的 $keytool 路径。'
    exit 1
}

if (Test-Path $ks) {
    Write-Host "keystore 已存在，不覆盖：$ks" -ForegroundColor Yellow
    Write-Host '如需重新生成，先手动删除该文件。'
    exit 0
}

& $keytool -genkeypair -v `
    -keystore $ks -alias phone `
    -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass kkl1nread -keypass kkl1nread `
    -dname "CN=KlinRead Phone, OU=Dev, O=KKL1n, C=CN"

Write-Host ""
Write-Host "已生成：$ks" -ForegroundColor Green
