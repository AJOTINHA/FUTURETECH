param([ValidateSet(16, 32, 64)][int]$Size = 32)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
# Historical artwork only; the current battery uses the open frame and port textures.
$destination = Join-Path $PSScriptRoot 'exported'
[System.IO.Directory]::CreateDirectory($destination) | Out-Null
$frontSource = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'source-fronts.png'))
$preview = [System.Drawing.Bitmap]::new(256, 256)
try {
    if ($frontSource.Width -ne $frontSource.Height -or $frontSource.Width % 2 -ne 0) {
        throw 'The source must be a square atlas with four equal quadrants.'
    }
    $tileSize = $frontSource.Width / 2
    $names = @('front')
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
                    $texture.SetPixel($x, $y, $color)
                }
            }
            $filename = 'battery_mk1_' + $names[$tile] + '.png'
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
