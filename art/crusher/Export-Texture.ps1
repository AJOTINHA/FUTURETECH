$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$repository=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$source=[System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'source-front.png'))
$texture=[System.Drawing.Bitmap]::new(32,32)
$preview=[System.Drawing.Bitmap]::new(512,512)
try {
    if($source.Width -ne $source.Height){throw 'Expected a square source'}
    for($y=0;$y -lt 32;$y++){for($x=0;$x -lt 32;$x++){
        $pixel=$source.GetPixel([int][Math]::Floor(($x+0.5)*$source.Width/32),[int][Math]::Floor(($y+0.5)*$source.Height/32))
        $texture.SetPixel($x,$y,[System.Drawing.Color]::FromArgb(255,$pixel.R,$pixel.G,$pixel.B))
    }}
    $texture.Save((Join-Path $repository 'src/main/resources/assets/futuretech/textures/block/crusher_front.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    for($y=0;$y -lt 512;$y++){for($x=0;$x -lt 512;$x++){
        $preview.SetPixel($x,$y,$texture.GetPixel([int][Math]::Floor($x/16),[int][Math]::Floor($y/16)))
    }}
    $preview.Save((Join-Path $PSScriptRoot 'preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
}finally{$source.Dispose();$texture.Dispose();$preview.Dispose()}
