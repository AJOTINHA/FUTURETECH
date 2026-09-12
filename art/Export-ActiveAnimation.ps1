param(
    [Parameter(Mandatory)][string]$TexturePath,
    [Parameter(Mandatory)][ValidateSet('furnace', 'generator')][string]$Kind
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
. (Join-Path $PSScriptRoot 'StatusDisplay.ps1')
$displayPalette = @(Get-StatusDisplayPalette)
$source = [System.Drawing.Bitmap]::new($TexturePath)
$size = $source.Width
$frameCount = 16
$strip = [System.Drawing.Bitmap]::new($size, ($size * $frameCount))
try {
    if ($source.Height -ne $size) { throw 'Export the static front before animating it.' }
    for ($frame = 0; $frame -lt $frameCount; $frame++) {
        $phase = 2 * [Math]::PI * $frame / $frameCount
        for ($y = 0; $y -lt $size; $y++) {
            for ($x = 0; $x -lt $size; $x++) {
                $color = $source.GetPixel($x, $y)
                $gridX = $x * 32.0 / $size
                $gridY = $y * 32.0 / $size
                # Only warm pixels inside the heating chamber may change.
                # The frame, grille, handle and status LED remain identical.
                $inside = $gridX -ge 6 -and $gridX -lt 26 -and $gridY -ge 10 -and $gridY -lt 27
                $warm = $color.R -gt 45 -and $color.R -gt $color.G * 1.3 -and $color.G -gt $color.B * 1.3
                if ($inside -and $warm) {
                    if ($Kind -eq 'furnace') {
                        $wave = $gridX * 0.55
                        $strength = 1 + 0.12 * [Math]::Sin($phase) + 0.16 * ([Math]::Sin($wave - $phase) - [Math]::Sin($wave))
                    } else {
                        # Periodic upward heat waves and independent column flicker.
                        $wave = $gridY * 0.85 + $gridX * 0.65
                        $column = $gridX * 1.4
                        $strength = 1 + 0.24 * ([Math]::Sin($wave + $phase * 2) - [Math]::Sin($wave)) + 0.12 * ([Math]::Sin($column + $phase * 3) - [Math]::Sin($column))
                    }
                    $red = [int][Math]::Min(255, [Math]::Max(0, [Math]::Round($color.R * $strength)))
                    $green = [int][Math]::Min(255, [Math]::Max(0, [Math]::Round($color.G * [Math]::Pow($strength, 1.5))))
                    $blue = [int][Math]::Min(255, [Math]::Max(0, [Math]::Round($color.B * $strength)))
                    $color = [System.Drawing.Color]::FromArgb(255, $red, $green, $blue)
                }
                if ($gridY -lt 10 -and $color.B -gt 70 -and $color.B -gt $color.R * 1.5) {
                    $position = if ($Kind -eq 'generator') { ($gridX - 11) * 8 / 10 } else { $gridX - 12 }
                    $index = [int][Math]::Min(7, [Math]::Max(0, [Math]::Floor($position)))
                    $baseBlue = $displayPalette[$index]
                    # A highlight travels across the same blue segments in both displays.
                    $brightness = 0.8 + 0.2 * [Math]::Cos($position * [Math]::PI / 4 - $phase)
                    $color = [System.Drawing.Color]::FromArgb(255, [int]($baseBlue.R * $brightness), [int]($baseBlue.G * $brightness), [int]($baseBlue.B * $brightness))
                }
                $strip.SetPixel($x, $frame * $size + $y, $color)
            }
        }
    }
} finally {
    $source.Dispose()
}
try {
    $strip.Save($TexturePath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $strip.Dispose()
}
$metadata = @{ animation = @{ width = $size; height = $size; frametime = 2; interpolate = ($Kind -eq 'furnace') } }
[System.IO.File]::WriteAllText(($TexturePath + '.mcmeta'), ($metadata | ConvertTo-Json -Depth 3), [System.Text.UTF8Encoding]::new($false))
Write-Output "$Kind animation: $frameCount frames, ${size}x${size} each, 2 ticks per frame"
