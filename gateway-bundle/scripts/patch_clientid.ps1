# LoongClaw Client ID Auto-Patch Script
# 在 Gateway 启动前运行，确保 loongclaw-desktop 在 client.id 白名单中
# 升级 OpenClaw 后第一次重启自动打补丁

$distDir = "$env:APPDATA\npm\node_modules\openclaw\dist"

# 找到 client-info JS 文件
$jsFiles = Get-ChildItem "$distDir\client-info-*.js"
if (-not $jsFiles) {
    Write-Output "[patch_clientid] No client-info JS file found"
    exit 1
}

# 找到包含实际 GATEWAY_CLIENT_IDS 定义的文件（不是 re-export 入口）
$jsFile = ($jsFiles | Where-Object { (Get-Content $_.FullName -Raw) -match 'ANDROID_APP' } | Select-Object -First 1).FullName
if (-not $jsFile) {
    Write-Output "[patch_clientid] No JS file with GATEWAY_CLIENT_IDS found"
    exit 1
}
$content = Get-Content $jsFile -Raw

# 检查是否已有 loongclaw-desktop
if ($content -match 'LOONGCLAW_DESKTOP') {
    Write-Output "[patch_clientid] loongclaw-desktop already in whitelist, skipping"
    exit 0
}

# 在 ANDROID_APP 后面插入 LOONGCLAW_DESKTOP
$newContent = $content -replace 'ANDROID_APP: "openclaw-android"', "ANDROID_APP: `"openclaw-android`",`n`tLOONGCLAW_DESKTOP: `"loongclaw-desktop`""

if ($newContent -eq $content) {
    Write-Output "[patch_clientid] Failed to patch: ANDROID_APP not found"
    exit 1
}

Set-Content $jsFile $newContent -NoNewline
Write-Output "[patch_clientid] Patched $($jsFile.Name) - added LOONGCLAW_DESKTOP"

# 同样更新 .d.ts 文件
$dtsFiles = Get-ChildItem "$distDir\client-info-*.d.ts"
foreach ($dtsFile in $dtsFiles) {
    $dtsContent = Get-Content $dtsFile.FullName -Raw
    if ($dtsContent -match 'LOONGCLAW_DESKTOP') {
        Write-Output "[patch_clientid] $($dtsFile.Name) already has LOONGCLAW_DESKTOP"
        continue
    }
    $newDts = $dtsContent -replace 'readonly ANDROID_APP: "openclaw-android";', "readonly ANDROID_APP: `"openclaw-android`";`n  readonly LOONGCLAW_DESKTOP: `"loongclaw-desktop`";"
    if ($newDts -ne $dtsContent) {
        Set-Content $dtsFile.FullName $newDts -NoNewline
        Write-Output "[patch_clientid] Patched $($dtsFile.Name)"
    }
}

# 更新 plugin-sdk 下的源 type 文件
$sdkDts = Get-ChildItem "$distDir\plugin-sdk\packages\gateway-protocol\src\client-info.d.ts"
if ($sdkDts) {
    $sdkContent = Get-Content $sdkDts.FullName -Raw
    if ($sdkContent -notmatch 'LOONGCLAW_DESKTOP') {
        $newSdk = $sdkContent -replace 'readonly ANDROID_APP: "openclaw-android";', "readonly ANDROID_APP: `"openclaw-android`";`n    readonly LOONGCLAW_DESKTOP: `"loongclaw-desktop`";"
        if ($newSdk -ne $sdkContent) {
            Set-Content $sdkDts.FullName $newSdk -NoNewline
            Write-Output "[patch_clientid] Patched plugin-sdk client-info.d.ts"
        }
    }
}

Write-Output "[patch_clientid] Done"
exit 0
