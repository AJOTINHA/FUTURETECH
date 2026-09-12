param([ValidateSet(16, 32, 64)][int]$Size = 32)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$destination = Join-Path $repository 'src/main/resources/assets/futuretech/textures/block/machine_side_input.png'
$source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'source.png'))
$texture = [System.Drawing.Bitmap]::new($Size, $Size)
$preview = [System.Drawing.Bitmap]::new(256, 256)
try {
    if ($source.Width -ne $source.Height) { throw 'The source texture must be square.' }
    for ($y = 0; $y -lt $Size; $y++) {
        for ($x = 0; $x -lt $Size; $x++) {
            $sourceX = [int][Math]::Floor(($x + 0.5) * $source.Width / $Size)
            $sourceY = [int][Math]::Floor(($y + 0.5) * $source.Height / $Size)
            $sample = $source.GetPixel($sourceX, $sourceY)
            # Preserve the selected artwork's RGB colors, but the machine must be opaque.
            $texture.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $sample.R, $sample.G, $sample.B))
        }
    }
    $texture.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
    for ($y = 0; $y -lt 256; $y++) {
        for ($x = 0; $x -lt 256; $x++) {
            $preview.SetPixel($x, $y, $texture.GetPixel([int][Math]::Floor($x * $Size / 256), [int][Math]::Floor($y * $Size / 256)))
        }
    }
    $preview.Save((Join-Path $PSScriptRoot 'preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "machine_side_input.png: $Size x $Size, opaque"
} finally {
    $preview.Dispose()
    $texture.Dispose()
    $source.Dispose()
}
