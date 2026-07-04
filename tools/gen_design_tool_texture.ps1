# Generates the 16x16 Glowstone Wrench item texture using System.Drawing.
# A metal spanner with a ring head that grips a glowing glowstone core.
# Edit the colour values / coordinate lists below and re-run to tweak the sprite.
Add-Type -AssemblyName System.Drawing

$W = 16
$H = 16
$bmp = New-Object System.Drawing.Bitmap($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    }
}

function Paint($cells, $a, $r, $g, $b) {
    $color = [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
    foreach ($c in $cells) {
        $x = $c[0]; $y = $c[1]
        if ($x -ge 0 -and $x -lt $W -and $y -ge 0 -and $y -lt $H) {
            $bmp.SetPixel($x, $y, $color)
        }
    }
}

# Metal ring head (top-right) and diagonal handle.
$ringMetal  = @((10,1),(11,1),(12,1),(9,2),(13,2),(9,3),(13,3),(9,4),(13,4),(10,5),(11,5),(12,5))
$ringShadow = @((9,4),(13,4),(10,5),(11,5),(12,5))
$shaftDark  = @((9,5),(8,6),(7,7),(6,8),(5,9),(4,10),(3,11),(2,12),(2,13))
$shaftLight = @((9,6),(8,7),(7,8),(6,9),(5,10),(4,11),(3,12),(3,13))

# Glowstone core inside the ring.
$glowMid    = @((10,2),(12,2),(12,3),(10,4),(11,4),(12,4))
$glowBright = @((10,3),(11,2))
$glowCore   = @((11,3))
$sparkle    = @((14,2),(3,13))

# Metal (dark first, then lighter highlights).
Paint $ringMetal  255 120 124 140
Paint $ringShadow 255 72 74 88
Paint $shaftDark  255 72 74 88
Paint $shaftLight 255 120 124 140

# Glowstone (amber gradient up to a bright core).
Paint $glowMid    255 240 185 80
Paint $glowBright 255 255 225 130
Paint $glowCore   255 255 250 205
Paint $sparkle    255 255 255 235

$out = Join-Path $PSScriptRoot "..\NeoForge-1.21.1-main\src\main\resources\assets\neonlight\textures\item\glowstone_wrench.png"
$dir = Split-Path $out
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "wrote $out"
