$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$textures = Join-Path $repository 'src/main/resources/assets/futuretech/textures/block'
$idle = [System.Drawing.Bitmap]::new((Join-Path $textures 'crusher_front.png'))
$source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'source-front-on.png'))
$active = [System.Drawing.Bitmap]::new(32, 32)
$frames = 10
$strip = [System.Drawing.Bitmap]::new(32, (32 * $frames))
$preview = [System.Drawing.Bitmap]::new(512, 512)
try {
    for ($y = 0; $y -lt 32; $y++) {
        for ($x = 0; $x -lt 32; $x++) {
            # Retain exact original casing pixels when the machine changes state.
            $color = $idle.GetPixel($x, $y)
            $intake = $x -ge 6 -and $x -le 25 -and $y -ge 10 -and $y -le 21
            $led = $y -eq 4 -and $x -ge 14 -and $x -le 17
            if ($intake -or $led) {
                $sample = $source.GetPixel([int][Math]::Floor(($x + 0.5) * $source.Width / 32),
                    [int][Math]::Floor(($y + 0.5) * $source.Height / 32))
                $color = [System.Drawing.Color]::FromArgb(255, $sample.R, $sample.G, $sample.B)
            }
            $active.SetPixel($x, $y, $color)
        }
    }
    for ($frame = 0; $frame -lt $frames; $frame++) {
        for ($y = 0; $y -lt 32; $y++) {
            for ($x = 0; $x -lt 32; $x++) {
                $sampleY = $y
                # Opposing five-row roller surfaces rotate toward the dark central gap.
                # Keep the end bearings still. Both periods divide the ten-frame loop.
                if ($x -ge 7 -and $x -le 24) {
                    if ($y -ge 10 -and $y -le 14) { $sampleY = 10 + (($y - 10 - ($frame % 5) + 5) % 5) }
                    if ($y -ge 17 -and $y -le 21) { $sampleY = 17 + (($y - 17 + $frame) % 5) }
                }
                $color = $active.GetPixel($x, $sampleY)
                if ($y -eq 4 -and $x -ge 14 -and $x -le 17) {
                    $strength = 0.85 + 0.15 * [Math]::Cos(2 * [Math]::PI * ($frame / $frames - ($x - 14) / 4))
                    $color = [System.Drawing.Color]::FromArgb(255, [int]($color.R * $strength),
                        [int]($color.G * $strength), [int]($color.B * $strength))
                }
                $strip.SetPixel($x, $frame * 32 + $y, $color)
            }
        }
    }
    $strip.Save((Join-Path $textures 'crusher_front_on.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    for ($y = 0; $y -lt 512; $y++) {
        for ($x = 0; $x -lt 512; $x++) {
            $preview.SetPixel($x, $y, $strip.GetPixel([int][Math]::Floor($x / 16), [int][Math]::Floor($y / 16)))
        }
    }
    $preview.Save((Join-Path $PSScriptRoot 'preview-on.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $idle.Dispose(); $source.Dispose(); $active.Dispose(); $strip.Dispose(); $preview.Dispose()
}
$metadata = @{ animation = @{ width = 32; height = 32; frametime = 2; interpolate = $false } }
[System.IO.File]::WriteAllText((Join-Path $textures 'crusher_front_on.png.mcmeta'),
    ($metadata | ConvertTo-Json -Depth 3), [System.Text.UTF8Encoding]::new($false))
Write-Output 'Crusher: 10 frames, 32x32 pixels, 2 ticks per frame (1-second loop).'
