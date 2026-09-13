$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output = Join-Path $repository 'src/main/resources/assets/futuretech/textures/item'
$preview = [System.Drawing.Bitmap]::new(768, 256)
try {
    $index = 0
    foreach ($metal in @('iron', 'gold', 'copper')) {
        $source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot "source-$metal.png"))
        $texture = [System.Drawing.Bitmap]::new(32, 32)
        try {
            if ($source.Width -ne $source.Height) { throw "Expected square source for $metal" }
            $transparent = 0
            $visible = 0
            for ($y = 0; $y -lt 32; $y++) { for ($x = 0; $x -lt 32; $x++) {
                $pixel = $source.GetPixel([int][Math]::Floor(($x + 0.5) * $source.Width / 32),
                    [int][Math]::Floor(($y + 0.5) * $source.Height / 32))
                # Discard near-transparent edge noise when reducing to the game's pixel grid.
                if ($pixel.A -lt 16) { $pixel = [System.Drawing.Color]::FromArgb(0, 0, 0, 0) }
                # Keep the generated alpha on the visible sprite.
                $texture.SetPixel($x, $y, $pixel)
                if ($pixel.A -eq 0) { $transparent++ }
                if ($pixel.A -gt 128) { $visible++ }
            } }
            if ($transparent -eq 0 -or $visible -eq 0) { throw "Missing sprite or transparency for $metal" }
            $texture.Save((Join-Path $output "${metal}_powder.png"), [System.Drawing.Imaging.ImageFormat]::Png)
            for ($y = 0; $y -lt 256; $y++) { for ($x = 0; $x -lt 256; $x++) {
                $preview.SetPixel($index * 256 + $x, $y,
                    $texture.GetPixel([int][Math]::Floor($x / 8), [int][Math]::Floor($y / 8)))
            } }
            Write-Output "${metal}: 32x32, $visible visible pixels, $transparent transparent pixels"
        } finally { $source.Dispose(); $texture.Dispose() }
        $index++
    }
    $preview.Save((Join-Path $PSScriptRoot 'preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $preview.Dispose() }
