<#
.SYNOPSIS
    Renders the Google Play listing graphics and the GitHub social preview of StreamsPlayer from the launcher glyph.

.DESCRIPTION
    docs/play/icon-512.png                    512 x 512 listing icon (Play applies its own mask, so the square is full bleed)
    docs/play/feature-graphic-1024x500.png    1024 x 500 feature graphic
    docs/github/social-preview-1280x640.png   1280 x 640 repository social preview (S0046 A6, uploaded in the repository
                                              settings by the owner)

    The geometry is the one of phone/src/main/res/drawable/ic_launcher_foreground.xml (and the watch copy): a white
    play triangle M42,34 L42,74 L76,54 on the canvas of 108 units centred at (54, 54), over the background colour
    ic_launcher_background #0B82EB. The listing icon shows the whole canvas instead of the 72-unit visible circle of a
    launcher, so the glyph is drawn 1.5 times larger. The pictures carry the product name only, so one file serves every
    listing language (Play falls back to the default language's graphics).
    Needs Windows (System.Drawing). Re-run it after changing the glyph; the PNGs are render targets, never edited.
    S0047 quiz decision of 2026-11-13; the shape of DeviceInfo's renderer.

.PARAMETER Root
    Repository root. Defaults to two levels above this script.
#>
[CmdletBinding()]
param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$background = [System.Drawing.ColorTranslator]::FromHtml('#0B82EB')
$name = 'StreamsPlayer'
$playDir = Join-Path $Root 'docs\play'
$githubDir = Join-Path $Root 'docs\github'
New-Item -ItemType Directory -Force $playDir, $githubDir | Out-Null

# Draws the play triangle with the canvas centre (54, 54) at ($cx, $cy); $unit is the size in pixels of one geometry unit.
function Draw-Glyph {
    param([System.Drawing.Graphics]$G, [single]$cx, [single]$cy, [single]$unit)
    $points = [System.Drawing.PointF[]]@(
        (New-Object System.Drawing.PointF(($cx + (42 - 54) * $unit), ($cy + (34 - 54) * $unit))),
        (New-Object System.Drawing.PointF(($cx + (42 - 54) * $unit), ($cy + (74 - 54) * $unit))),
        (New-Object System.Drawing.PointF(($cx + (76 - 54) * $unit), ($cy + (54 - 54) * $unit)))
    )
    $G.FillPolygon([System.Drawing.Brushes]::White, $points)
}

function New-Canvas {
    param([int]$Width, [int]$Height)
    $bmp = New-Object System.Drawing.Bitmap($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear($background)
    [pscustomobject]@{ Bitmap = $bmp; Graphics = $g }
}

# The glyph on the left, the name on the right, the name's size fitted to the box it has.
function Save-Banner {
    param([int]$Width, [int]$Height, [single]$GlyphX, [single]$Unit, [single]$TextX, [single]$MaxFont, [string]$Path)
    $c = New-Canvas -Width $Width -Height $Height
    Draw-Glyph -G $c.Graphics -cx $GlyphX -cy ($Height / 2) -unit $Unit
    $box = $Width - $TextX - ($Width * 0.04)
    $size = $MaxFont
    while ($true) {
        $font = New-Object System.Drawing.Font('Segoe UI Semibold', $size, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
        if ($c.Graphics.MeasureString($name, $font).Width -le $box -or $size -le 24) { break }
        $font.Dispose()
        $size -= 2
    }
    try {
        $format = New-Object System.Drawing.StringFormat
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center
        $c.Graphics.DrawString($name, $font, [System.Drawing.Brushes]::White, (New-Object System.Drawing.RectangleF($TextX, 0, $box, $Height)), $format)
    }
    finally { $font.Dispose() }
    $c.Graphics.Dispose()
    $c.Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $c.Bitmap.Dispose()
}

# Listing icon.
$icon = New-Canvas -Width 512 -Height 512
Draw-Glyph -G $icon.Graphics -cx 256 -cy 256 -unit ((512 / 108) * 1.5)
$icon.Graphics.Dispose()
$iconPath = Join-Path $playDir 'icon-512.png'
$icon.Bitmap.Save($iconPath, [System.Drawing.Imaging.ImageFormat]::Png)
$icon.Bitmap.Dispose()

$featurePath = Join-Path $playDir 'feature-graphic-1024x500.png'
Save-Banner -Width 1024 -Height 500 -GlyphX 200 -Unit 5.2 -TextX 380 -MaxFont 96 -Path $featurePath

$socialPath = Join-Path $githubDir 'social-preview-1280x640.png'
Save-Banner -Width 1280 -Height 640 -GlyphX 250 -Unit 6.6 -TextX 475 -MaxFont 120 -Path $socialPath

foreach ($path in $iconPath, $featurePath, $socialPath) {
    $img = [System.Drawing.Image]::FromFile($path)
    Write-Host ("{0}: {1} x {2}, {3} bytes" -f (Split-Path $path -Leaf), $img.Width, $img.Height, (Get-Item $path).Length)
    $img.Dispose()
}
