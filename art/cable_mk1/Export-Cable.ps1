$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$assets = Join-Path $repository 'src/main/resources/assets/futuretech'
$atlas = [System.Drawing.Bitmap]::new(32, 32)
$palette = @{
    D = '#202829'; H = '#D0D5CC'; M = '#969F9A'; S = '#566361'
    # Energy bar palette: blue shadow, cyan body and the exact ENERGY_END highlight.
    G = '#1676C4'; A = '#28B8D3'; Y = '#55E7ED'; W = '#AEF5F7'
}
foreach ($key in @($palette.Keys)) { $palette[$key] = [System.Drawing.ColorTranslator]::FromHtml($palette[$key]) }
try {
    for ($y=0; $y -lt 32; $y++) { for ($x=0; $x -lt 32; $x++) { $atlas.SetPixel($x,$y,$palette.D) } }
    # Rotated opposite arms reverse the texture across their shared seam.
    # Symmetric rail highlights keep both halves aligned in all six directions.
    for ($y=0; $y -lt 16; $y++) {
        for ($x=0; $x -lt 4; $x++) { $atlas.SetPixel(16+$x,$y,$palette[[string]'DHHD'[$x]]) }
    }
    $atlas.SetPixel(24,0,$palette.D)
    # Recessed, enclosed cable face: silver shoulders around a narrow cyan stripe.
    # Centre the conductor exactly. Both the cross-section and the length pattern
    # are mirrored, so north/south, east/west and up/down have identical edge texels.
    for ($y=0; $y -lt 12; $y++) {
        $distanceFromEnd = [Math]::Min($y,11-$y)
        $row = if ($distanceFromEnd % 4 -lt 2) { 'DMHAYYAHMD' } else { 'DMHGAAGHMD' }
        for ($x=0; $x -lt 10; $x++) { $atlas.SetPixel(16+$x,18+$y,$palette[[string]$row[$x]]) }
    }
    $contact = @('DDDDDDDD','DHHHHHMD','DHAYAGMD','DHYWAGMD','DHAYYGMD','DHAGGGMD','DMMMMMSD','DDDDDDDD')
    for ($y=0; $y -lt 8; $y++) { for ($x=0; $x -lt 8; $x++) { $atlas.SetPixel($x,16+$y,$palette[[string]$contact[$y][$x]]) } }
    $atlas.Save((Join-Path $assets 'textures/block/cable_mk1.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally { $atlas.Dispose() }

# One complete square design shared by every outward piece of the node frame.
# Four texels per model unit match the raised rails without cutting corner highlights.
$nodeAtlas = [System.Drawing.Bitmap]::new(32,32)
try {
    for ($y=0;$y -lt 32;$y++) { for ($x=0;$x -lt 32;$x++) { $nodeAtlas.SetPixel($x,$y,$palette.D) } }
    for ($y=0;$y -lt 24;$y++) { for ($x=0;$x -lt 24;$x++) {
        $edge = [Math]::Min([Math]::Min($x,23-$x),[Math]::Min($y,23-$y))
        $color = if ($edge -in @(1,2)) { $palette.H } else { $palette.D }
        if ($x -ge 4 -and $x -lt 20 -and $y -ge 4 -and $y -lt 20) {
            $cx=[int][Math]::Floor(($x-4)/2); $cy=[int][Math]::Floor(($y-4)/2)
            $color=$palette[[string]$contact[$cy][$cx]]
        }
        $nodeAtlas.SetPixel(4+$x,4+$y,$color)
    } }
    $nodeAtlas.Save((Join-Path $assets 'textures/block/cable_mk1_node.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally { $nodeAtlas.Dispose() }

$textures = @{cable='futuretech:block/cable_mk1';node='futuretech:block/cable_mk1_node';particle='futuretech:block/cable_mk1'}
function Face($uv, [int]$rotation=0) {
    $face = @{texture='#cable';uv=$uv}
    if ($rotation) { $face.rotation = $rotation }
    return $face
}
function Box($from, $to, [string]$material='dark', [int]$axis=2) {
    $faces = @{}
    # UV axes of each vanilla cuboid face; rotate stripes along the beam.
    $axes = @{north=@(0,1);south=@(0,1);east=@(2,1);west=@(2,1);up=@(0,2);down=@(0,2)}
    foreach ($side in @('down','up','north','south','west','east')) {
        $uv = @(12,0,12.5,0.5)
        $rotation = 0
        if ($material -eq 'sleeve' -and $axis -in $axes[$side]) { $uv = @(8,9,13,15) }
        if ($material -eq 'contact') { $uv = @(0,8,4,12) }
        if ($material -eq 'rail' -and $axis -in $axes[$side]) { $uv = @(8,0,10,8) }
        if ($material -in @('sleeve','rail') -and $axes[$side][0] -eq $axis) { $rotation=90 }
        $faces[$side] = Face $uv $rotation
    }
    return @{from=$from;to=$to;faces=$faces}
}
function NodeFace($from, $to, [string]$side) {
    # Vanilla face UV projections, anchored to the complete 5..11 node bounds.
    $coordinates = switch ($side) {
        down  { @($from[0],(16-$to[2]),$to[0],(16-$from[2])) }
        up    { @($from[0],$from[2],$to[0],$to[2]) }
        north { @((16-$to[0]),(16-$to[1]),(16-$from[0]),(16-$from[1])) }
        south { @($from[0],(16-$to[1]),$to[0],(16-$from[1])) }
        west  { @($from[2],(16-$to[1]),$to[2],(16-$from[1])) }
        east  { @((16-$to[2]),(16-$to[1]),(16-$from[2]),(16-$from[1])) }
    }
    return @{texture='#node';uv=@($coordinates | ForEach-Object { 2+($_-5)*2 })}
}
function NodeRail($from, $to) {
    $piece=Box $from $to
    $planes=@{down=$from[1];up=$to[1];north=$from[2];south=$to[2];west=$from[0];east=$to[0]}
    foreach ($side in @('down','up','north','south','west','east')) {
        if ($planes[$side] -in @(5,11)) { $piece.faces[$side]=NodeFace $from $to $side }
    }
    return $piece
}
function Joint {
    # Dark recessed housing, twelve independent raised frame edges, six inset contacts.
    Box @(5.75,5.75,5.75) @(10.25,10.25,10.25)
    foreach ($x in @(5,10)) { foreach ($z in @(5,10)) {
        NodeRail @($x,5,$z) @(($x+1),11,($z+1))
    } }
    foreach ($y in @(5,10)) { foreach ($z in @(5,10)) {
        NodeRail @(6,$y,$z) @(10,($y+1),($z+1))
    } }
    foreach ($x in @(5,10)) { foreach ($y in @(5,10)) {
        NodeRail @($x,$y,6) @(($x+1),($y+1),10)
    } }
    for ($axis=0; $axis -lt 3; $axis++) { foreach ($high in @($false,$true)) {
        $from = @(6,6,6); $to = @(10,10,10)
        $from[$axis] = if ($high) { 10.25 } else { 5.625 }
        $to[$axis] = if ($high) { 10.375 } else { 5.75 }
        $piece=Box $from $to
        $side = if ($high) { @('east','up','south')[$axis] } else { @('west','down','north')[$axis] }
        $piece.faces[$side]=NodeFace $from $to $side
        $piece
    } }
}
function Arm([bool]$south, [bool]$item) {
    $end = if ($south) { 'south' } else { 'north' }
    $z1 = if ($south) { 11 } else { 0 }
    $z2 = if ($south) { 16 } else { 5 }
    # The core reaches the recessed central housing, not just the outer frame plane.
    $coreZ1 = if ($south) { 10.25 } else { 0 }
    $coreZ2 = if ($south) { 16 } else { 5.75 }
    $pieces = @(
        # Enclosed inset faces, half a unit below the raised rails on all four sides.
        Box @(5.5,5.5,$coreZ1) @(10.5,10.5,$coreZ2) 'sleeve'
        foreach ($x in @(5,10)) { foreach ($y in @(5,10)) {
            Box @($x,$y,$z1) @(($x+1),($y+1),$z2) 'rail' 2
        } }
    )
    foreach ($piece in $pieces) {
        if (-not $item) { $piece.faces[$end].cullface = $end }
        $piece
    }
}
function SaveModel([string]$path, $elements, [bool]$item=$false) {
    $model = [ordered]@{textures=$textures;elements=@($elements)}
    if ($item) {
        $model.parent='minecraft:block/block'
        # Enlarge the six-unit node in inventory slots while keeping its 3D shape.
        $model.display=@{gui=@{rotation=@(30,225,0);translation=@(0,0,0);scale=@(1.5,1.5,1.5)}}
    }
    $model | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $assets $path) -Encoding utf8
}
SaveModel 'models/block/cable_mk1_center.json' (Joint)
SaveModel 'models/block/cable_mk1_side.json' (Arm $false $false)
$itemElements = @(Joint)
SaveModel 'models/item/cable_mk1.json' $itemElements $true
Write-Output 'Cabo MK1 3D: faces prateadas recuadas, faixa ciano estreita e trilhos elevados.'
& (Join-Path $PSScriptRoot 'Export-Connector.ps1')
