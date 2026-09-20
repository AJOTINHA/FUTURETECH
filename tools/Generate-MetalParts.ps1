# Native Minecraft cuboid models using the corresponding vanilla metal texture.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../src/main/resources'))
$assets = Join-Path $root 'assets/futuretech'
function Write-Json($path, $value) {
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
    [IO.File]::WriteAllText($path, (($value | ConvertTo-Json -Depth 30) + "`n"), [Text.UTF8Encoding]::new($false))
}
function Box($name, $from, $to, $uv) {
    $faces = [ordered]@{}
    foreach ($side in @('north','south','east','west','up','down')) {
        $faces[$side] = @{ texture = '#metal'; uv = $uv }
    }
    return @{ name = $name; from = $from; to = $to; faces = $faces }
}

# A 32-cell radial silhouette gives the diagonal teeth the same size as the axial teeth.
# Heights form a machined outer rim, recessed web and raised axle collar, on both sides.
function New-GearElements($palette) {
    $cells = @{}
    for ($y=0; $y -lt 32; $y++) { for ($x=0; $x -lt 32; $x++) {
        $dx = $x + 0.5 - 16
        $dy = $y + 0.5 - 16
        $radius = [Math]::Sqrt($dx*$dx + $dy*$dy)
        if ($radius -lt 3.6) { continue }
        $tooth = $false
        for ($index=0; $index -lt 8; $index++) {
            $angle = $index * [Math]::PI / 4
            $radial = [Math]::Round($dx * [Math]::Cos($angle) + $dy * [Math]::Sin($angle), 8)
            $tangent = [Math]::Round(-$dx * [Math]::Sin($angle) + $dy * [Math]::Cos($angle), 8)
            # Squared working teeth with a small bevel at their tips.
            $halfWidth = if ($radial -gt 13.5) { 1.5 } else { 2.2 }
            if ($radial -ge 9.5 -and $radial -le 14.5 -and [Math]::Abs($tangent) -le $halfWidth) { $tooth = $true }
        }
        if ($radius -gt 11.2 -and -not $tooth) { continue }
        $depth = if ($radius -lt 4.4) { 1.0 }
            elseif ($radius -lt 6.2) { 1.3 }
            elseif ($radius -lt 7.0) { 0.9 }
            elseif ($radius -lt 9.5) { 0.55 }
            elseif ($radius -lt 10.5) { 0.95 }
            else { 0.75 }
        $cells["$x,$y"] = $depth
    } }
    $elements = @()
    foreach ($key in ($cells.Keys | Sort-Object)) {
        $x, $y = $key.Split(',') | ForEach-Object { [int]$_ }
        $depth = $cells[$key]
        $tone = if ($depth -eq 0.55 -or $depth -eq 0.9) { 'dark' }
            elseif ($depth -eq 1.3 -or $depth -eq 0.95) { 'light' }
            else { 'mid' }
        $sample = $palette[$tone]
        $uv = @(($sample[0]+0.2),($sample[1]+0.2),($sample[0]+0.8),($sample[1]+0.8))
        $element = Box "gear_${x}_$y" @(($x/2.0),(15.5-$y/2.0),(8-$depth)) @((0.5+$x/2.0),(16-$y/2.0),(8+$depth)) $uv
        # Two broad reflections cross the machined faces like the plate's metal bands.
        # Keep the recessed web darker than the rim, and leave edge walls at their base tone.
        if (($y -ge 5 -and $y -le 8) -or ($y -ge 19 -and $y -le 21)) {
            $reflection = switch ($tone) { 'dark' { 'mid' } 'mid' { 'light' } 'light' { 'peak' } }
            $highlight = $palette[$reflection]
            $highlightUV = @(($highlight[0]+0.2),($highlight[1]+0.2),($highlight[0]+0.8),($highlight[1]+0.8))
            $element.faces.north.uv = $highlightUV
            $element.faces.south.uv = $highlightUV
        }
        $neighbors = @{ west = "$($x-1),$y"; east = "$($x+1),$y"; up = "$x,$($y-1)"; down = "$x,$($y+1)" }
        foreach ($side in $neighbors.Keys) {
            if ($cells.ContainsKey($neighbors[$side]) -and $cells[$neighbors[$side]] -ge $depth) { $element.faces.Remove($side) }
        }
        $elements += $element
    }
    return $elements
}
# Samples from the vanilla metal blocks: shadow, body and machined highlights.
$palettes = @{
    iron = @{ dark = @(12,15); mid = @(8,6); light = @(8,4); peak = @(4,4) }
    steel = @{ dark = @(12,15); mid = @(8,6); light = @(8,4); peak = @(4,4) }
    gold = @{ dark = @(15,2); mid = @(0,1); light = @(14,3); peak = @(8,1) }
    copper = @{ dark = @(6,15); mid = @(14,13); light = @(10,14); peak = @(0,12) }
    netherite = @{ dark = @(15,15); mid = @(6,10); light = @(14,11); peak = @(8,5) }
    tin = @{ dark = @(12,15); mid = @(8,6); light = @(8,4); peak = @(4,4) }
    lead = @{ dark = @(12,15); mid = @(8,6); light = @(8,4); peak = @(4,4) }
    silver = @{ dark = @(12,15); mid = @(8,6); light = @(8,4); peak = @(4,4) }
}
# Metals drawn as iron tinted: the item model multiplies the texture by this ARGB.
$tints = @{ steel = -6380890; tin = -3547930; lead = -8681832; silver = -856088 }
foreach ($metal in @('iron','gold','copper','netherite','steel','tin','lead','silver')) {
    foreach ($kind in @('plate','gear')) {
        $id = "${metal}_$kind"
        if ($kind -eq 'plate') {
            $elements = @(
                (Box 'thin_plate_edge' @(2,2,7.7) @(14,14,8.3) @(1,1,15,15)),
                (Box 'front_bevel' @(2.4,2.4,7.5) @(13.6,13.6,7.7) @(2,2,14,14)),
                (Box 'back_bevel' @(2.4,2.4,8.3) @(13.6,13.6,8.5) @(2,2,14,14))
            )
            $rotation = @(15,-20,-10)
        } else {
            $elements = @(New-GearElements $palettes[$metal])
            $rotation = @(15,-20,0)
        }
        $textureMetal = if ($tints.ContainsKey($metal)) { 'iron' } else { $metal }
        if ($tints.ContainsKey($metal)) {
            foreach ($element in $elements) { foreach ($face in $element.faces.Values) { $face.tintindex = 0 } }
        }
        Write-Json (Join-Path $assets "models/item/metal_parts/$id.json") @{
            parent = 'minecraft:block/block'; ambientocclusion = $false; gui_light = 'front'
            textures = @{ metal = "minecraft:block/${textureMetal}_block"; particle = "minecraft:block/${textureMetal}_block" }
            elements = $elements
            display = @{
                gui = @{ rotation = $rotation; translation = $(if ($kind -eq 'plate') { @(0,0.5,0) } else { @(0,0,0) }); scale = @(0.95,0.95,0.95) }
                ground = @{ translation = @(0,3,0); scale = @(0.5,0.5,0.5) }
                fixed = @{ rotation = @(0,180,0); scale = @(0.85,0.85,0.85) }
                firstperson_righthand = @{ rotation = @(0,-30,5); scale = @(0.75,0.75,0.75) }
                firstperson_lefthand = @{ rotation = @(0,30,-5); scale = @(0.75,0.75,0.75) }
                thirdperson_righthand = @{ rotation = @(0,-90,0); translation = @(0,2,0); scale = @(0.65,0.65,0.65) }
                thirdperson_lefthand = @{ rotation = @(0,90,0); translation = @(0,2,0); scale = @(0.65,0.65,0.65) }
            }
        }
        $itemModel = @{ type = 'minecraft:model'; model = "futuretech:item/metal_parts/$id" }
        if ($tints.ContainsKey($metal)) { $itemModel.tints = @(@{ type='minecraft:constant'; value=$tints[$metal] }) }
        Write-Json (Join-Path $assets "items/$id.json") @{ model = $itemModel }
        Write-Json (Join-Path $root "data/c/tags/item/${kind}s/$metal.json") @{ replace = $false; values = @("futuretech:$id") }
    }
}
foreach ($kind in @('plate','gear')) {
    Write-Json (Join-Path $root "data/c/tags/item/${kind}s.json") @{
        replace = $false; values = @('iron','gold','copper','netherite','steel','tin','lead','silver' | ForEach-Object { "#c:${kind}s/$_" })
    }
}
