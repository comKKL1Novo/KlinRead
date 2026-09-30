# 直接运行单元测试，绕开 Gradle 的 forked worker。
#
# 背景：在当前环境下 `gradlew test` 会失败，报
#   ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain
# 这是 Gradle 无法 fork 测试 worker 进程导致的环境问题，不是代码问题。
# 这些测试是纯 JVM 逻辑测试，不需要 worker 隔离，直接用 JUnitCore 跑即可。
#
# 用法：
#   pwsh tools\run-tests.ps1
#   powershell -File tools\run-tests.ps1
#
# 在 Android Studio 里点绿三角跑测试不受此限制，那是另一条路径。

$ErrorActionPreference = 'Continue'

$JdkHome    = 'C:\Software\AndroidStudio\jbr'
$AndroidHome = 'C:\Software\ASDK'
$KotlinVersion = '2.0.21'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$env:JAVA_HOME = $JdkHome
$env:ANDROID_HOME = $AndroidHome

# ── 1. 编译测试代码 ─────────────────────────────────────────────────────────
Write-Host '[1/3] 编译测试代码...' -ForegroundColor Cyan
& "$root\gradlew.bat" compileDebugUnitTestKotlin --console=plain -q
if ($LASTEXITCODE -ne 0) {
    Write-Host '编译失败，测试无法运行。' -ForegroundColor Red
    exit 1
}

# ── 2. 组装类路径 ───────────────────────────────────────────────────────────
$testClasses = Join-Path $root 'app\build\tmp\kotlin-classes\debugUnitTest'
$mainClasses = Join-Path $root 'app\build\tmp\kotlin-classes\debug'

# Gradle 缓存路径里带哈希，所以用递归查找而不是写死路径
$cacheRoot = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'

function Find-Jar($pattern) {
    $hit = Get-ChildItem -Path $cacheRoot -Recurse -Filter $pattern -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } |
        Select-Object -First 1
    return $hit.FullName
}

$junit    = Find-Jar 'junit-4.13.2.jar'
$hamcrest = Find-Jar 'hamcrest-core-1.3.jar'
$stdlib   = Find-Jar "kotlin-stdlib-$KotlinVersion.jar"

$missing = @()
if (-not $junit)    { $missing += 'junit-4.13.2.jar' }
if (-not $hamcrest) { $missing += 'hamcrest-core-1.3.jar' }
if (-not $stdlib)   { $missing += "kotlin-stdlib-$KotlinVersion.jar" }

if ($missing.Count -gt 0) {
    Write-Host "找不到依赖：$($missing -join ', ')" -ForegroundColor Red
    Write-Host '请先执行一次 gradlew build，让 Gradle 把依赖下载下来。'
    exit 1
}

$classpath = @($testClasses, $mainClasses, $junit, $hamcrest, $stdlib) -join ';'
$java = Join-Path $JdkHome 'bin\java.exe'

# ── 3. 逐个运行测试类 ───────────────────────────────────────────────────────
$testClassesToRun = @(
    'com.klin.read.reader.ChapterSplitterTest',
    'com.klin.read.reader.TextDecoderTest',
    'com.klin.read.ui.music.MusicFilterTest'
)

$failed = @()
$totalTests = 0

for ($i = 0; $i -lt $testClassesToRun.Count; $i++) {
    $name = $testClassesToRun[$i]
    Write-Host ""
    Write-Host "[$($i + 2)/3] $name" -ForegroundColor Cyan

    $output = & $java '-Dfile.encoding=UTF-8' -cp $classpath org.junit.runner.JUnitCore $name 2>&1
    $code = $LASTEXITCODE
    $output | ForEach-Object { Write-Host $_ }

    # 从 JUnit 输出里把用例数抠出来做汇总
    $match = [regex]::Match(($output -join "`n"), 'OK \((\d+) tests?\)')
    if ($match.Success) { $totalTests += [int]$match.Groups[1].Value }

    if ($code -ne 0) { $failed += $name }
}

Write-Host ""
if ($failed.Count -eq 0) {
    Write-Host "全部通过：$totalTests 个测试。" -ForegroundColor Green
    exit 0
} else {
    Write-Host "失败的测试类：$($failed -join ', ')" -ForegroundColor Red
    exit 1
}
