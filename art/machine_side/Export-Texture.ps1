param(
    [ValidateSet(16, 32, 64)][int]$Size = 32,
    [ValidateRange(0, 12)][int]$DetailStrength = 0
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$destination = Join-Path $repository 'src/main/resources/assets/futuretech/textures/block/machine_side.png'
$source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'source.png'))
$texture = [System.Drawing.Bitmap]::new($Size, $Size)
$preview = [System.Drawing.Bitmap]::new(256, 256)
# Small, irregular tonal patches on the 32-pixel design grid: x, y, width, height, tone.
# Fixed positions keep every export identical. No seams, vents or changes to the frame.
$metalDetails = @(
    @(7, 6, 3, 1, 1), @(19, 5, 2, 2, -1), @(24, 9, 2, 1, 1),
    @(12, 10, 2, 2, -1), @(5, 14, 2, 1, 1), @(19, 13, 3, 1, 1),
    @(9, 18, 2, 2, -1), @(24, 18, 2, 1, -1), @(15, 20, 2, 1, 1),
    @(6, 24, 3, 1, -1), @(21, 25, 3, 1, 1), @(13, 27, 2, 1, -1)
)
try {
    if ($source.Width -ne $source.Height) { throw 'The source texture must be square.' }
    for ($y = 0; $y -lt $Size; $y++) {
        for ($x = 0; $x -lt $Size; $x++) {
            $sourceX = [int][Math]::Floor(($x + 0.5) * $source.Width / $Size)
            $sourceY = [int][Math]::Floor(($y + 0.5) * $source.Height / $Size)
            $sample = $source.GetPixel($sourceX, $sourceY)
            $gridX = [int][Math]::Floor(($x + 0.5) * 32 / $Size)
            $gridY = [int][Math]::Floor(($y + 0.5) * 32 / $Size)
            $tone = 0
            if ($gridX -ge 4 -and $gridX -le 27 -and $gridY -ge 4 -and $gridY -le 27) {
                # Slightly strengthen the existing metal grain, keeping its original hue.
                $luminance = ($sample.R + $sample.G + $sample.B) / 3.0
                $tone = [int][Math]::Round([Math]::Clamp(($luminance - 128) / 30.0, -0.5, 0.5) * $DetailStrength)
                foreach ($detail in $metalDetails) {
                    if ($gridX -ge $detail[0] -and $gridX -lt ($detail[0] + $detail[2]) -and
                        $gridY -ge $detail[1] -and $gridY -lt ($detail[1] + $detail[3])) {
                        $tone += $detail[4] * $DetailStrength
                    }
                }
            }
            $red = [int][Math]::Clamp($sample.R + $tone, 0, 255)
            $green = [int][Math]::Clamp($sample.G + $tone, 0, 255)
            $blue = [int][Math]::Clamp($sample.B + $tone, 0, 255)
            $texture.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $red, $green, $blue))
        }
    }
    $texture.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
    for ($y = 0; $y -lt 256; $y++) {
        for ($x = 0; $x -lt 256; $x++) {
            $preview.SetPixel($x, $y, $texture.GetPixel([int][Math]::Floor($x * $Size / 256), [int][Math]::Floor($y * $Size / 256)))
        }
    }
    $preview.Save((Join-Path $PSScriptRoot 'preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "machine_side.png: $Size x $Size, opaque, detail strength $DetailStrength"
} finally {
    $preview.Dispose()
    $texture.Dispose()
    $source.Dispose()
}
