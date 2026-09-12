param([switch]$ConnectorCloseup)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
if (-not ('CablePreviewRaster' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'PreviewRaster.cs') -ReferencedAssemblies @('System.Drawing.Common','System.Drawing.Primitives') }
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$assets = Join-Path $repository 'src/main/resources/assets/futuretech'
$texture = [System.Drawing.Bitmap]::new((Join-Path $assets 'textures/block/cable_mk1.png'))
$nodeTexture = [System.Drawing.Bitmap]::new((Join-Path $assets 'textures/block/cable_mk1_node.png'))
$connectorTexture = [System.Drawing.Bitmap]::new((Join-Path $assets 'textures/block/cable_connector.png'))
$machineTexture = [System.Drawing.Bitmap]::new((Join-Path $assets 'textures/block/machine_side.png'))
$connector = Get-Content (Join-Path $PSScriptRoot 'connector-preview-model.json') -Raw | ConvertFrom-Json
$center = Get-Content (Join-Path $assets 'models/block/cable_mk1_center.json') -Raw | ConvertFrom-Json
$arm = Get-Content (Join-Path $assets 'models/block/cable_mk1_side.json') -Raw | ConvertFrom-Json
$faces = [System.Collections.Generic.List[object]]::new()
function Transform($p, [int]$rx, [int]$ry, $offset) {
    $x=$p[0]-8; $y=$p[1]-8; $z=$p[2]-8
    for($i=0;$i -lt $rx;$i+=90){$old=$y;$y=$z;$z=-$old}
    for($i=0;$i -lt $ry;$i+=90){$old=$x;$x=-$z;$z=$old}
    return @(($x+8+$offset[0]),($y+8+$offset[1]),($z+8+$offset[2]))
}
function AddModel($model, $offset, [int]$rx=0, [int]$ry=0) {
    foreach($element in $model.elements) {
        $a=$element.from; $b=$element.to
        $vertices=@{
            north=@(@($b[0],$b[1],$a[2]),@($a[0],$b[1],$a[2]),@($b[0],$a[1],$a[2]))
            south=@(@($a[0],$b[1],$b[2]),@($b[0],$b[1],$b[2]),@($a[0],$a[1],$b[2]))
            east=@(@($b[0],$b[1],$b[2]),@($b[0],$b[1],$a[2]),@($b[0],$a[1],$b[2]))
            west=@(@($a[0],$b[1],$a[2]),@($a[0],$b[1],$b[2]),@($a[0],$a[1],$a[2]))
            up=@(@($a[0],$b[1],$a[2]),@($b[0],$b[1],$a[2]),@($a[0],$b[1],$b[2]))
            down=@(@($a[0],$a[1],$b[2]),@($b[0],$a[1],$b[2]),@($a[0],$a[1],$a[2]))
        }
        $normals=@{north=@(0,0,-1);south=@(0,0,1);east=@(1,0,0);west=@(-1,0,0);up=@(0,1,0);down=@(0,-1,0)}
        foreach($property in $element.faces.PSObject.Properties) {
            $n=$normals[$property.Name]
            $normal=Transform @(($n[0]+8),($n[1]+8),($n[2]+8)) $rx $ry @(-8,-8,-8)
            if($normal[0]+$normal[1]-$normal[2] -le 0){continue}
            $points=@(foreach($v in $vertices[$property.Name]){ ,(Transform $v $rx $ry $offset) })
            $mid=@(0,0,0)
            for($i=0;$i -lt 3;$i++){$mid[$i]=($points[1][$i]+$points[2][$i])/2}
            $faces.Add(@{points=$points;depth=($mid[0]+$mid[1]-$mid[2]);face=$property.Value;light=$(if($normal[1] -gt 0){1.0}elseif($normal[0] -gt 0){0.72}else{0.88})})
        }
    }
}
function Cable($offset, [string[]]$connections) {
    AddModel $center $offset
    foreach($connection in $connections){
        switch($connection){
            north { AddModel $arm $offset }
            east { AddModel $arm $offset 0 90 }
            south { AddModel $arm $offset 0 180 }
            west { AddModel $arm $offset 0 270 }
            up { AddModel $arm $offset 270 0 }
            down { AddModel $arm $offset 90 0 }
        }
    }
}
if (-not $ConnectorCloseup) {
Cable @(0,0,0) @('east')
Cable @(16,0,0) @('west','east')
Cable @(32,0,0) @('west','east','south')
Cable @(48,0,0) @('west','east')
Cable @(64,0,0) @('west','up')
Cable @(64,16,0) @('up','down')
Cable @(64,32,0) @('down','up')
AddModel $connector @(64,32,0) 270 0
Cable @(32,0,16) @('north','south')
Cable @(32,0,32) @('north','south')
AddModel $connector @(32,0,32) 0 180
} else {
    Cable @(0,0,0) @('west','east')
    AddModel $connector @(0,0,0) 0 270
    AddModel $connector @(0,0,0) 0 90
}
$machineFaces=@{}
foreach($side in @('north','south','east','west','up','down')){$machineFaces[$side]=@{texture='#machine';uv=@(0,0,16,16)}}
$machine=@{elements=@(@{from=@(0,0,0);to=@(16,16,16);faces=$machineFaces})} | ConvertTo-Json -Depth 8 | ConvertFrom-Json
if (-not $ConnectorCloseup) {
    AddModel $machine @(64,48,0)
    AddModel $machine @(32,0,48)
} else {
    AddModel $machine @(-16,0,0)
    AddModel $machine @(16,0,0)
}
$viewScale=if($ConnectorCloseup){14}else{8}
$viewX=if($ConnectorCloseup){330}else{100}
$viewY=if($ConnectorCloseup){450}else{370}
$bitmap=[System.Drawing.Bitmap]::new(1100,680)
$g=[System.Drawing.Graphics]::FromImage($bitmap)
try {
    $g.Clear([System.Drawing.ColorTranslator]::FromHtml('#222A30'))
    $g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $depths=[double[]]::new($bitmap.Width*$bitmap.Height)
    [Array]::Fill($depths,[double]::NegativeInfinity)
    foreach($entry in $faces){
        $uv=$entry.face.uv
        $source = switch ($entry.face.texture) { '#node' { $nodeTexture } '#connector' { $connectorTexture } '#machine' { $machineTexture } default { $texture } }
        $scale=$source.Width/16
        $rect=[System.Drawing.Rectangle]::new($uv[0]*$scale,$uv[1]*$scale,($uv[2]-$uv[0])*$scale,($uv[3]-$uv[1])*$scale)
        $tile=$source.Clone($rect,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try{
            if($entry.face.rotation -eq 90){$tile.RotateFlip([System.Drawing.RotateFlipType]::Rotate90FlipNone)}
            [double[]]$projected=@(foreach($p in $entry.points){
                $viewX+($p[0]+$p[2])*$viewScale
                $viewY-$p[1]*$viewScale+($p[0]-$p[2])*$viewScale*0.4625
                $p[0]+0.925*$p[1]-$p[2]
            })
            [CablePreviewRaster]::Face($bitmap,$depths,$tile,$projected,$entry.light)
        }finally{$tile.Dispose()}
    }
    $font=[System.Drawing.Font]::new('Segoe UI',22,[System.Drawing.FontStyle]::Bold)
    $small=[System.Drawing.Font]::new('Segoe UI',11)
    try{
        $title=if($ConnectorCloseup){'CONECTOR - LATERAIS'}else{'CABO MK1 - 3D'}
        $g.DrawString($title,$font,[System.Drawing.Brushes]::White,36,24)
        $g.DrawString('PREVIA DOS MODELOS  |  FUTURETECH',$small,[System.Drawing.Brushes]::Silver,39,65)
    }finally{$font.Dispose();$small.Dispose()}
    $previewName=if($ConnectorCloseup){'connector-closeup.png'}else{'preview.png'}
    $bitmap.Save((Join-Path $PSScriptRoot $previewName),[System.Drawing.Imaging.ImageFormat]::Png)
}finally{$g.Dispose();$bitmap.Dispose();$texture.Dispose();$nodeTexture.Dispose();$connectorTexture.Dispose();$machineTexture.Dispose()}
