param([ValidateSet(16, 32, 64)][int]$Size = 32)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
. (Join-Path $PSScriptRoot '../StatusDisplay.ps1')
$displayPalette = @(Get-StatusDisplayPalette)
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$destination = Join-Path $repository 'src/main/resources/assets/futuretech/textures/block'
[System.IO.Directory]::CreateDirectory($destination) | Out-Null
$frontSource = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'source-fronts.png'))
$preview = [System.Drawing.Bitmap]::new(512, 256)
try {
    if ($frontSource.Width -ne $frontSource.Height -or $frontSource.Width % 2 -ne 0) {
        throw 'The source must be a square atlas with four equal quadrants.'
    }
    $tileSize = $frontSource.Width / 2
    $names = @('front', 'front_on')
    for ($tile = 0; $tile -lt $names.Count; $tile++) {
        $column = $tile % 2
        $row = [int][Math]::Floor($tile / 2)
        $texture = [System.Drawing.Bitmap]::new($Size, $Size)
        try {
            # Sample pixel centers without smoothing so Minecraft keeps crisp edges.
            for ($y = 0; $y -lt $Size; $y++) {
                for ($x = 0; $x -lt $Size; $x++) {
                    $sourceX = [int][Math]::Floor($column * $tileSize + ($x + 0.5) * $tileSize / $Size)
                    $sourceY = [int][Math]::Floor($row * $tileSize + ($y + 0.5) * $tileSize / $Size)
                    $sample = $frontSource.GetPixel($sourceX, $sourceY)
                    # Machine casing is solid; generated source alpha must not leak into block textures.
                    $color = [System.Drawing.Color]::FromArgb(255, $sample.R, $sample.G, $sample.B)
                    $gridX = $x * 32.0 / $Size
                    $gridY = $y * 32.0 / $Size
                    if ($tile -eq 1 -and $gridY -lt 10 -and $color.B -gt 70 -and $color.B -gt $color.R * 1.5) {
                        $index = [int][Math]::Min(7, [Math]::Max(0, [Math]::Floor(($gridX - 11) * 8 / 10)))
                        $blue = $displayPalette[$index]
                        $color = [System.Drawing.Color]::FromArgb(255, $blue.R, $blue.G, $blue.B)
                    }
                    $texture.SetPixel($x, $y, $color)
                }
            }
            $filename = 'solid_fuel_generator_' + $names[$tile] + '.png'
            $texture.Save((Join-Path $destination $filename), [System.Drawing.Imaging.ImageFormat]::Png)
            for ($y = 0; $y -lt 256; $y++) {
                for ($x = 0; $x -lt 256; $x++) {
                    $color = $texture.GetPixel([int][Math]::Floor($x * $Size / 256), [int][Math]::Floor($y * $Size / 256))
                    $preview.SetPixel($column * 256 + $x, $row * 256 + $y, $color)
                }
            }
            Write-Output "$filename : $Size x $Size"
        } finally {
            $texture.Dispose()
        }
    }
    $preview.Save((Join-Path $PSScriptRoot 'preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $preview.Dispose()
    $frontSource.Dispose()
}
& (Join-Path $PSScriptRoot '../Export-ActiveAnimation.ps1') -TexturePath (Join-Path $destination 'solid_fuel_generator_front_on.png') -Kind generator
