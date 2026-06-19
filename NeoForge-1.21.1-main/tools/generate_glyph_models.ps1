$OUT = Join-Path $PSScriptRoot "..\src\main\resources\assets\neonlight\models\block"
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

function New-Face {
    return @{
        uv       = @(0, 0, 2, 2)
        texture  = "#0"
        tintindex = 0
    }
}

function New-Box {
    param([int]$x1, [int]$y1, [int]$x2, [int]$y2)
    $face = New-Face
    return @{
        from     = @($x1, $y1, 7.5)
        to       = @($x2, $y2, 8.5)
        rotation = @{ angle = 0; axis = "y"; origin = @(8, 8, 8) }
        faces    = @{
            north = $face
            south = $face
            east  = $face
            west  = $face
            up    = $face
            down  = $face
        }
    }
}

function New-HBar {
    param([int]$x1, [int]$x2, [int]$y)
    New-Box $x1 $y $x2 ($y + 1)
}

function New-VBar {
    param([int]$x, [int]$y1, [int]$y2)
    New-Box $x $y1 ($x + 1) $y2
}

$seg = @{
    a = (New-HBar 6 10 10)
    b = (New-VBar 10 7 10)
    c = (New-VBar 10 5 8)
    d = (New-HBar 6 10 5)
    e = (New-VBar 6 5 8)
    f = (New-VBar 6 7 10)
    g = (New-HBar 6 10 7)
}

$digits = @{
    "0" = "abcdef"
    "1" = "bc"
    "2" = "abdeg"
    "3" = "abcdg"
    "4" = "bcfg"
    "5" = "acdfg"
    "6" = "acdefg"
    "7" = "abc"
    "8" = "abcdefg"
    "9" = "abcdfg"
}

function Write-GlyphModel {
    param([string]$Name, [array]$Elements)
    $obj = [ordered]@{
        format_version = "1.21.11"
        credit         = "Generated for Neon Lights"
        render_type    = "minecraft:translucent"
        textures       = @{
            "0"       = "neonlight:block/glyph_base"
            particle  = "neonlight:block/glyph_base"
        }
        elements       = $Elements
    }
    $json = ($obj | ConvertTo-Json -Depth 20) -replace '(\d+)\.0\b', '$1'
    Set-Content -Encoding UTF8 -Path (Join-Path $OUT "$Name.json") -Value $json
}

foreach ($entry in $digits.GetEnumerator()) {
    $elements = @()
    foreach ($segment in $entry.Value.ToCharArray()) {
        $elements += $seg[[string]$segment]
    }
    Write-GlyphModel "number_$($entry.Key)" $elements
}

Write-GlyphModel "symbol_exclamation" @(
    (New-VBar 8 6 10),
    (New-Box 8 5 9 6)
)
Write-GlyphModel "symbol_at" @(
    $seg.a, $seg.d, $seg.f, $seg.g,
    (New-VBar 10 6 9),
    (New-HBar 7 9 8),
    (New-Box 7 6 8 7)
)
Write-GlyphModel "symbol_hash" @(
    (New-VBar 7 6 9),
    (New-VBar 10 6 9),
    (New-HBar 6 11 8),
    (New-HBar 6 11 6)
)
Write-GlyphModel "symbol_dollar" @(
    $seg.a, $seg.d, $seg.f, $seg.g,
    (New-VBar 10 7 10),
    (New-VBar 6 5 7),
    (New-HBar 6 10 7)
)
Write-GlyphModel "symbol_percent" @(
    (New-Box 6 9 7 10),
    (New-Box 9 5 10 6),
    (New-Box 7 7 8 8),
    (New-Box 8 6 9 7),
    (New-Box 6 5 7 6)
)
Write-GlyphModel "symbol_caret" @(
    (New-Box 6 8 7 10),
    (New-Box 9 8 10 10),
    (New-HBar 7 9 9)
)
Write-GlyphModel "symbol_ampersand" @(
    $seg.a, $seg.d, $seg.e, $seg.g,
    (New-VBar 10 6 8),
    (New-HBar 7 9 7),
    (New-Box 8 8 9 9)
)
Write-GlyphModel "symbol_asterisk" @(
    (New-HBar 7 9 8),
    (New-VBar 8 6 9),
    (New-Box 7 7 8 8),
    (New-Box 9 7 10 8),
    (New-Box 7 9 8 10),
    (New-Box 9 9 10 10)
)
Write-GlyphModel "symbol_paren_open" @(
    (New-VBar 6 5 11),
    (New-HBar 6 10 10),
    (New-HBar 6 10 5)
)
Write-GlyphModel "symbol_paren_close" @(
    (New-VBar 10 5 11),
    (New-HBar 7 11 10),
    (New-HBar 7 11 5)
)
Write-GlyphModel "symbol_minus" @((New-HBar 6 10 7))
Write-GlyphModel "symbol_underscore" @((New-HBar 5 11 5))
Write-GlyphModel "symbol_period" @((New-Box 8 5 9 6))
Write-GlyphModel "symbol_comma" @((New-Box 8 5 9 6), (New-Box 7 4 8 5))
Write-GlyphModel "symbol_question" @(
    $seg.a, $seg.b, $seg.g,
    (New-Box 8 5 9 6),
    (New-VBar 6 8 10)
)
Write-GlyphModel "arrow_up" @(
    (New-VBar 8 5 8),
    (New-HBar 6 10 9),
    (New-Box 6 8 7 9),
    (New-Box 9 8 10 9)
)
Write-GlyphModel "arrow_down" @(
    (New-VBar 8 6 10),
    (New-HBar 6 10 6),
    (New-Box 6 6 7 7),
    (New-Box 9 6 10 7)
)
Write-GlyphModel "arrow_left" @(
    (New-HBar 6 9 8),
    (New-VBar 5 8 8),
    (New-Box 6 9 7 10),
    (New-Box 6 6 7 7)
)
Write-GlyphModel "arrow_right" @(
    (New-HBar 7 10 8),
    (New-VBar 9 8 8),
    (New-Box 9 6 10 7),
    (New-Box 9 9 10 10)
)

Write-Host "Generated glyph models in $OUT"
