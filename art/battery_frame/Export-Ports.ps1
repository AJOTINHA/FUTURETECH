$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$destination = Join-Path $repository 'src/main/resources/assets/futuretech/textures/block'
$steel = [System.Drawing.Bitmap]::new((Join-Path $destination 'machine_side.png'))
function Color($hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
try {
    foreach ($mode in @('input','output')) {
        $port = [System.Drawing.Bitmap]::new(64,64)
        $g = [System.Drawing.Graphics]::FromImage($port)
        $base = Color $(if ($mode -eq 'input') { '#008FFF' } else { '#EC761C' })
        $light = Color $(if ($mode -eq 'input') { '#65CBFF' } else { '#FFBF68' })
        $shade = Color $(if ($mode -eq 'input') { '#07558C' } else { '#8C4315' })
        $deep = Color $(if ($mode -eq 'input') { '#163C56' } else { '#583923' })
        try {
            for ($y=0; $y -lt 64; $y++) { for ($x=0; $x -lt 64; $x++) {
                $port.SetPixel($x,$y,$steel.GetPixel([int][Math]::Floor($x/2),[int][Math]::Floor($y/2)))
            } }
            # Restrained shading follows the actual ledges; colors match the other machines.
            foreach ($layer in @(@(17,30,'#959AA4'),@(18,28,'#454B55'),@(19,26,'#222830'))) {
                $brush=[System.Drawing.SolidBrush]::new((Color $layer[2]))
                $g.FillRectangle($brush,[int]$layer[0],[int]$layer[0],[int]$layer[1],[int]$layer[1]); $brush.Dispose()
            }
            for ($y=20; $y -lt 44; $y++) { for ($x=20; $x -lt 44; $x++) {
                $c=$base
                if ($x -eq 20 -or $y -eq 20) { $c=$light }
                if ($x -eq 43 -or $y -eq 43) { $c=$shade }
                if (($x -eq 23 -and $y -ge 23 -and $y -le 40) -or ($y -eq 23 -and $x -ge 23 -and $x -le 40)) { $c=$deep }
                if (($x -eq 40 -and $y -ge 24 -and $y -le 40) -or ($y -eq 40 -and $x -ge 24 -and $x -le 40)) { $c=$light }
                if ($x -ge 24 -and $x -lt 40 -and $y -ge 24 -and $y -lt 40) { $c=Color '#18212A' }
                $port.SetPixel($x,$y,$c)
            } }
            # Small corner retainers in the frame's metal palette.
            foreach ($cx in @(21,41)) { foreach ($cy in @(21,41)) {
                $port.SetPixel($cx,$cy,(Color '#B0B4BC'))
                $port.SetPixel($cx+1,$cy,(Color '#797D87'))
                $port.SetPixel($cx,$cy+1,(Color '#454B55'))
                $port.SetPixel($cx+1,$cy+1,(Color '#252B33'))
            } }
            # Reserved upper strip: depth shading for all four walls of the colored sleeve.
            for ($y=0; $y -lt 8; $y++) { for ($x=0; $x -lt 64; $x++) {
                $c = switch ($y) { 0 {$base} 1 {$shade} 2 {$deep} 3 {$shade} 4 {$shade} 5 {$deep} 6 {$shade} 7 {$base} }
                if (($y -eq 0 -or $y -eq 7) -and (($x-20) % 5 -eq 1)) { $c=$light }
                $port.SetPixel($x,$y,$c)
            } }
            $port.Save((Join-Path $destination "battery_port_$mode.png"),[System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $g.Dispose(); $port.Dispose() }
    }
} finally { $steel.Dispose() }
Write-Output 'Detailed battery port textures exported'
