$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
& (Join-Path $PSScriptRoot 'Export-Ports.ps1')
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$destination = Join-Path $repository 'src/main/resources/assets/futuretech/textures/block'
$source = [System.Drawing.Bitmap]::new((Join-Path $destination 'battery_frame.png'))
try {
    foreach ($mode in @('', '_input', '_output')) {
        $icon = [System.Drawing.Bitmap]::new(64, 64)
        $port = if ($mode -ne '') { [System.Drawing.Bitmap]::new((Join-Path $destination ('battery_port' + $mode + '.png'))) } else { $null }
        try {
            for ($y = 0; $y -lt 64; $y++) { for ($x = 0; $x -lt 64; $x++) {
                $sx = if ($x -ge 52) { $x - 52 } else { $x }
                $sy = if ($y -ge 52) { $y - 52 } else { $y }
                if (($x -lt 12 -or $x -ge 52) -and ($y -lt 12 -or $y -ge 52)) { $color = $source.GetPixel($sx, 16 + $sy) }
                elseif ($x -ge 12 -and $x -lt 52 -and (($y -ge 1 -and $y -lt 11) -or ($y -ge 53 -and $y -lt 63))) { $color = $source.GetPixel($x - 12, $(if ($y -ge 53) { $y - 53 } else { $y - 1 })) }
                elseif ($y -ge 12 -and $y -lt 52 -and (($x -ge 1 -and $x -lt 11) -or ($x -ge 53 -and $x -lt 63))) { $color = $source.GetPixel(48 + $(if ($x -ge 53) { $x - 53 } else { $x - 1 }), $y - 12) }
                else { $color = [System.Drawing.Color]::Transparent }
                # Group each axis explicitly: PowerShell evaluates -and/-or left to right.
                # The bottom plate row must reach the beam instead of exposing the mode background.
                if ($mode -ne '' -and $x -ge 11 -and $x -lt 53 -and $y -ge 11 -and $y -lt 53 -and (($x -ge 12 -and $x -lt 52) -or ($y -ge 12 -and $y -lt 52))) {
                    $color = $port.GetPixel($x,$y)
                    if ($x -ge 24 -and $x -lt 40 -and $y -ge 24 -and $y -lt 40) { $color = [System.Drawing.Color]::Transparent }
                }
                $icon.SetPixel($x, $y, $color)
            } }
            $icon.Save((Join-Path $destination ('battery_frame_preview' + $mode + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $icon.Dispose(); if ($null -ne $port) { $port.Dispose() } }
    }
} finally { $source.Dispose() }
Write-Output 'Battery side modes and GUI previews exported'
