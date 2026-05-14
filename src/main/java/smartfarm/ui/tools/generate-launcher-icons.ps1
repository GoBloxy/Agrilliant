# B7 — Android launcher icon generator (PowerShell / .NET variant).
#
# Mirrors smartfarm.ui.tools.LauncherIconGenerator (Java) but runs
# on stock Windows without needing Maven, JavaFX, or a compiled
# project. Useful for quickly (re-)generating icons in a worktree
# where the full project toolchain isn't set up.
#
# Reads:    src/main/resources/images/logo.png
# Writes:   src/android/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
#             ic_launcher.png
#             ic_launcher_round.png
#
# Both variants render the source logo centred at 72% of the icon
# edge on the brand-green (#2e7d32) background. The _round.png
# variant uses an antialiased ellipse background with a circular
# clip on the logo so launchers that don't auto-mask still display
# a clean round icon.
#
# Run from anywhere — paths are resolved relative to this script,
# so the project root is auto-detected:
#
#   pwsh -File src/main/java/smartfarm/ui/tools/generate-launcher-icons.ps1
#
# OR equivalently from the project root:
#
#   pwsh src/main/java/smartfarm/ui/tools/generate-launcher-icons.ps1

[CmdletBinding()]
param(
    [string] $LogoPath,
    [string] $OutRoot
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

# ── Paths (resolved relative to this script so the dir-of-invocation
#    doesn't matter) ──────────────────────────────────────────────
$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
# tools/ → ui/ → smartfarm/ → java/ → main/ → src/ → <project root>: six levels up.
$projectRoot = Resolve-Path (Join-Path $scriptDir '..\..\..\..\..\..')

if (-not $LogoPath) {
    $LogoPath = Join-Path $projectRoot 'src\main\resources\images\logo.png'
}
if (-not $OutRoot) {
    $OutRoot = Join-Path $projectRoot 'src\android\res'
}

if (-not (Test-Path $LogoPath)) {
    Write-Error "Source logo not found: $LogoPath"
    exit 1
}

# ── Generator parameters (match LauncherIconGenerator.java) ─────
$BackgroundColor = [System.Drawing.Color]::FromArgb(255, 46, 125, 50)  # #2e7d32, opaque
$LogoScale       = 0.72                                                 # ~14% margin per side

$Densities = @(
    @{ name = 'mdpi';    px = 48  },
    @{ name = 'hdpi';    px = 72  },
    @{ name = 'xhdpi';   px = 96  },
    @{ name = 'xxhdpi';  px = 144 },
    @{ name = 'xxxhdpi'; px = 192 }
)

# ── Load source logo once ───────────────────────────────────────
$logo = [System.Drawing.Image]::FromFile($LogoPath)
Write-Host "Source logo: $LogoPath ($($logo.Width)x$($logo.Height))"
Write-Host "Output root: $OutRoot"
Write-Host ""

$bgBrush = New-Object System.Drawing.SolidBrush $BackgroundColor

$written = 0
try {
    foreach ($d in $Densities) {
        $size = [int] $d.px
        $dir  = Join-Path $OutRoot ("mipmap-{0}" -f $d.name)
        New-Item -ItemType Directory -Path $dir -Force | Out-Null

        foreach ($variant in @('square', 'round')) {
            $bmp = New-Object System.Drawing.Bitmap $size, $size,
                ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

            $g = [System.Drawing.Graphics]::FromImage($bmp)
            $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

            if ($variant -eq 'round') {
                # Antialiased circular background; everything outside stays transparent.
                $g.FillEllipse($bgBrush, 0, 0, $size, $size)
                # Clip the logo draw to the circle so it can't bleed outside.
                $clipPath = New-Object System.Drawing.Drawing2D.GraphicsPath
                $clipPath.AddEllipse(0, 0, $size, $size)
                $g.SetClip($clipPath)
                $clipPath.Dispose()
            } else {
                $g.FillRectangle($bgBrush, 0, 0, $size, $size)
            }

            # Aspect-preserving fit of the logo into the LogoScale box.
            $targetEdge = $size * $LogoScale
            $scale = [Math]::Min($targetEdge / $logo.Width, $targetEdge / $logo.Height)
            $drawW = $logo.Width  * $scale
            $drawH = $logo.Height * $scale
            $drawX = ($size - $drawW) / 2.0
            $drawY = ($size - $drawH) / 2.0
            $g.DrawImage($logo, [float]$drawX, [float]$drawY, [float]$drawW, [float]$drawH)

            $g.Dispose()

            $fileName = if ($variant -eq 'round') { 'ic_launcher_round.png' } else { 'ic_launcher.png' }
            $outPath = Join-Path $dir $fileName
            $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
            $bmp.Dispose()

            $written++
        }

        Write-Host ("  [{0,-8} {1,3} px] ic_launcher.png, ic_launcher_round.png" -f $d.name, $size)
    }
} finally {
    $bgBrush.Dispose()
    $logo.Dispose()
}

Write-Host ""
Write-Host "Done. Wrote $written icon files under $OutRoot"
