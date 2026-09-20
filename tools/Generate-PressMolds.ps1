$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$assets = "$root/src/main/resources/assets/futuretech"
function Write-Json($path,$value) {
    [IO.Directory]::CreateDirectory((Split-Path $path -Parent)) | Out-Null
    [IO.File]::WriteAllText($path,($value | ConvertTo-Json -Depth 40)+"`n",[Text.UTF8Encoding]::new($false))
}
function Box($from,$to,$texture,$uv) {
    $faces=[ordered]@{}
    foreach($face in @('north','south','east','west','up','down')) { $faces[$face]=@{ texture=$texture; uv=$uv } }
    return @{ from=$from;to=$to;faces=$faces }
}
# A steel die with a dark impression; native models match the existing plates and gears.
foreach($kind in @('plate','gear')) {
    $parts=[Collections.Generic.List[object]]::new()
    $parts.Add((Box @(1,1,7) @(15,15,9) '#steel' @(1,1,15,15)))
    $parts.Add((Box @(2,2,6.8) @(14,14,7) '#body' @(1,1,15,15)))
    foreach($x in @(1.5,13.5)) { foreach($y in @(1.5,13.5)) {
        $parts.Add((Box @($x,$y,6.5) @(($x+1),($y+1),6.8) '#steel' @(4,4,5,5)))
    } }
    if($kind -eq 'plate') {
        $parts.Add((Box @(4,4,6.6) @(12,12,6.8) '#steel' @(7,7,8,8)))
        $parts.Add((Box @(4.5,4.5,6.5) @(11.5,11.5,6.6) '#cavity' @(0,0,16,16)))
    } else {
        $gear=Get-Content "$assets/models/item/metal_parts/iron_gear.json" -Raw | ConvertFrom-Json
        foreach($element in $gear.elements) {
            # Project the established tooth silhouette into the die face.
            $x1=8+($element.from[0]-8)*0.68; $x2=8+($element.to[0]-8)*0.68
            $y1=8+($element.from[1]-8)*0.68; $y2=8+($element.to[1]-8)*0.68
            $parts.Add(@{from=@($x1,$y1,6.6);to=@($x2,$y2,6.6);faces=@{north=@{texture='#cavity';uv=@(0,0,16,16)}}})
        }
        $parts.Add((Box @(7.1,7.1,6.5) @(8.9,8.9,6.7) '#steel' @(4,4,5,5)))
    }
    # The die impression is visible from either side in the inventory and in hand.
    foreach ($front in @($parts.ToArray() | Select-Object -Skip 1)) {
        $back = $front | ConvertTo-Json -Depth 30 | ConvertFrom-Json -AsHashtable
        $back.from[2] = 16 - $front.to[2]
        $back.to[2] = 16 - $front.from[2]
        if ($back.faces.Count -eq 1 -and $back.faces.Contains('north')) {
            $back.faces.south = $back.faces.north
            $back.faces.Remove('north')
        }
        $parts.Add($back)
    }
    $model=@{parent='minecraft:block/block';gui_light='front';ambientocclusion=$false; textures=@{
        steel='minecraft:block/iron_block';body='minecraft:block/light_gray_concrete';cavity='minecraft:block/black_concrete';particle='minecraft:block/iron_block'
    };elements=$parts.ToArray();display=@{
        gui=@{rotation=@(10,-15,-5);translation=@(0,0.5,0);scale=@(0.9,0.9,0.9)}
        ground=@{translation=@(0,3,0);scale=@(0.5,0.5,0.5)}
        fixed=@{rotation=@(0,180,0);scale=@(0.85,0.85,0.85)}
        firstperson_righthand=@{rotation=@(0,-30,5);scale=@(0.75,0.75,0.75)}
        firstperson_lefthand=@{rotation=@(0,30,-5);scale=@(0.75,0.75,0.75)}
    }}
    Write-Json "$assets/models/item/press_molds/${kind}_mold.json" $model
    Write-Json "$assets/items/${kind}_mold.json" @{model=@{type='minecraft:model';model="futuretech:item/press_molds/${kind}_mold"}}
    # The molds are made in the Assembler (src/main/recipes/assembler.json), not on the crafting table.
}
