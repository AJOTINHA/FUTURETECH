$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$destination = Join-Path $repository 'src/main/resources/assets/futuretech/textures/block'
$machine = [System.Drawing.Bitmap]::new((Join-Path $destination 'machine_side.png'))
$atlas = [System.Drawing.Bitmap]::new(64, 64)
function MixColor($a, $b, [double]$amount) {
    [System.Drawing.Color]::FromArgb(255, [int]($a.R + ($b.R - $a.R) * $amount), [int]($a.G + ($b.G - $a.G) * $amount), [int]($a.B + ($b.B - $a.B) * $amount))
}
try {
    $dark = $machine.GetPixel(0, 0)
    $metal = $machine.GetPixel(16, 16)
    $silver = $machine.GetPixel(1, 1)
    $black = $machine.GetPixel(2, 3)
    $body = MixColor $dark $metal 0.62
    $light = MixColor $metal $silver 0.08
    $shade = MixColor $dark $black 0.35
    for ($y=0; $y -lt 64; $y++) { for ($x=0; $x -lt 64; $x++) { $atlas.SetPixel($x,$y,$dark) } }
    # Broad steel face with a shallow inset and one restrained joint at each end.
    $bands = @($dark,$light,$metal,$body,$body,$body,$body,$body,$dark,$shade)
    for ($x=0; $x -lt 40; $x++) {
        for ($y=0; $y -lt 10; $y++) {
            $color = $bands[$y]
            if ($x -ge 7 -and $x -lt 33 -and $y -in @(4,5)) { $color = MixColor $body $dark 0.5 }
            if ($x -ge 7 -and $x -lt 33 -and $y -eq 6) { $color = MixColor $body $metal 0.25 }
            if ($x -in @(4,35) -and $y -ge 2 -and $y -lt 8) { $color = MixColor $body $dark 0.75 }
            if ($x -in @(3,36) -and $y -ge 2 -and $y -lt 8) { $color = MixColor $body $metal 0.5 }
            $atlas.SetPixel($x,$y,$color)
            $atlas.SetPixel(48+$y,$x,$color)
        }
    }
    # One small fastener seated in a subtle square recess.
    $corner = @(
        'DDHHHHHHHHDD',
        'DHLMMMMMMLDD',
        'HLMMMMMMMMLD',
        'HMMRRRRRRMMD',
        'HMMRMMMMRMMD',
        'HMMRMSSMRMMD',
        'HMMRMLDMRMMD',
        'HMMRMMMMRMMD',
        'HMMRRRRRRMMD',
        'HLMMMMMMMMLD',
        'DDLMMMMMMLDD',
        'DDDDDDDDDDDD'
    )
    $palette = @{D=$shade;H=$light;L=$metal;M=$body;S=$silver;B=$black;R=(MixColor $body $dark 0.6)}
    for ($y=0;$y -lt 12;$y++) {for($x=0;$x -lt 12;$x++) {$atlas.SetPixel($x,16+$y,$palette[[string]$corner[$y][$x]])}}
    # End caps are mostly hidden in the reinforced joints.
    for($y=0;$y -lt 10;$y++){for($x=0;$x -lt 10;$x++){$atlas.SetPixel(16+$x,16+$y,$body)}}
    $atlas.Save((Join-Path $destination 'battery_frame.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    $preview=[System.Drawing.Bitmap]::new(256,256)
    try {
        for($y=0;$y -lt 256;$y++){for($x=0;$x -lt 256;$x++){$preview.SetPixel($x,$y,$atlas.GetPixel([int][Math]::Floor($x/4),[int][Math]::Floor($y/4)))}}
        $preview.Save((Join-Path $PSScriptRoot 'texture-preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally {$preview.Dispose()}
} finally {$atlas.Dispose();$machine.Dispose()}
& (Join-Path $PSScriptRoot 'Export-SideModes.ps1')
$icon=[System.Drawing.Bitmap]::new((Join-Path $destination 'battery_frame_preview.png'))
$front=[System.Drawing.Bitmap]::new(384,384)
try {
    for($y=0;$y -lt 384;$y++){for($x=0;$x -lt 384;$x++){$front.SetPixel($x,$y,$icon.GetPixel([int][Math]::Floor($x/6),[int][Math]::Floor($y/6)))}}
    $front.Save((Join-Path $PSScriptRoot 'front-preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally {$icon.Dispose();$front.Dispose()}
Write-Output 'battery_frame.png: 64x64, opaque, clean steel frame'
