# Read the approved eight blue pixels from the furnace artwork at its 32px grid.
function Get-StatusDisplayPalette {
    $reference = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'electric_furnace/source-front.png'))
    try {
        for ($column = 12; $column -lt 20; $column++) {
            $x = [int][Math]::Floor(($column + 0.5) * $reference.Width / 32)
            $y = [int][Math]::Floor(4.5 * $reference.Height / 32)
            $reference.GetPixel($x, $y)
        }
    } finally { $reference.Dispose() }
}
