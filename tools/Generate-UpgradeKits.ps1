# Native cuboid kit models use the existing machine materials and tier corner colors.
$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot '../src/main/resources'
$assets = Join-Path $root 'assets/futuretech'
function Write-Json($path, $value) {
    $absolute = [IO.Path]::GetFullPath($path)
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($absolute)) | Out-Null
    [IO.File]::WriteAllText($absolute, (($value | ConvertTo-Json -Depth 30) + "`n"), [Text.UTF8Encoding]::new($false))
}
function Box($name, $from, $to, $texture, $uv) {
    $faces = [ordered]@{}
    foreach ($side in @('north','south','east','west','up','down')) {
        $faces[$side] = @{ texture = "#$texture"; uv = $uv }
    }
    return @{ name = $name; from = $from; to = $to; faces = $faces }
}
foreach ($mk in 2..4) {
    $id = "upgrade_kit_mk$mk"
    $elements = @(
        (Box 'case' @(2,2,6) @(14,12,10) 'steel' @(0,0,16,16)),
        (Box 'handle_left' @(5,12,7) @(6,15,9) 'steel' @(1.5,1.5,1.9,1.9)),
        (Box 'handle_right' @(10,12,7) @(11,15,9) 'steel' @(1.5,1.5,1.9,1.9)),
        (Box 'handle_top' @(6,14,7) @(10,15,9) 'steel' @(1.5,1.5,1.9,1.9))
    )
    foreach ($z in @(5.8,10.0)) {
        $elements += Box 'colored_panel' @(3,4,$z) @(13,10,($z+0.2)) 'accent' @(0.1,0.1,0.4,0.4)
        $frontZ = if ($z -lt 6) { 5.6 } else { 10.2 }
        $elements += Box 'label_plate' @(4,5,$frontZ) @(12,9,($frontZ+0.2)) 'steel' @(1.5,1.5,1.9,1.9)
        $markZ = if ($z -lt 6) { 5.4 } else { 10.4 }
        for ($i=0; $i -lt $mk; $i++) {
            $x = 8 - ($mk*1.5-0.5)/2 + $i*1.5
            $elements += Box 'level_mark' @($x,5.6,$markZ) @(($x+1),8.4,($markZ+0.2)) 'steel' @(0.5,0.5,0.9,0.9)
        }
    }
    $model = [ordered]@{
        parent = 'minecraft:block/block'; ambientocclusion = $false; gui_light = 'front'
        textures = @{ steel = 'futuretech:block/machine/machine_side'; accent = "futuretech:block/machine/mk$mk/machine_side"; particle = 'futuretech:block/machine/machine_side' }
        elements = $elements
        display = @{
            gui = @{ rotation = @(15,-25,0); translation = @(0,-0.5,0); scale = @(1,1,1) }
            ground = @{ translation = @(0,3,0); scale = @(0.5,0.5,0.5) }
            fixed = @{ rotation = @(0,180,0); scale = @(0.8,0.8,0.8) }
            firstperson_righthand = @{ rotation = @(0,-30,0); translation = @(0,0,0); scale = @(0.7,0.7,0.7) }
            firstperson_lefthand = @{ rotation = @(0,30,0); translation = @(0,0,0); scale = @(0.7,0.7,0.7) }
            thirdperson_righthand = @{ rotation = @(0,-90,0); translation = @(0,1,0); scale = @(0.6,0.6,0.6) }
            thirdperson_lefthand = @{ rotation = @(0,90,0); translation = @(0,1,0); scale = @(0.6,0.6,0.6) }
        }
    }
    Write-Json (Join-Path $assets "models/item/$id.json") $model
    Write-Json (Join-Path $assets "items/$id.json") @{ model = @{ type = 'minecraft:model'; model = "futuretech:item/$id" } }
    # The crafting recipe is balanced by hand in data/futuretech/recipe/ and is not written here. The advancement is kept with it.
}

foreach ($machine in @('solid_fuel_generator','electric_furnace','crusher')) {
    $variants = [ordered]@{}
    foreach ($mk in 1..4) {
        foreach ($lit in @('false','true')) {
            $suffix = if ($lit -eq 'true') { '_on' } else { '' }
            $modelId = if ($mk -eq 1) { "$machine$suffix" } else { "$machine/mk$mk$suffix" }
            $direction = 0
            foreach ($facing in @('north','east','south','west')) {
                $variants["facing=$facing,lit=$lit,mk=$mk"] = @{ model = "futuretech:block/$modelId"; y = $direction*90 }
                $direction++
            }
            if ($mk -gt 1) {
                Write-Json (Join-Path $assets "models/block/$modelId.json") @{
                    parent = 'futuretech:block/machine_base'
                    textures = @{ side = "futuretech:block/machine/mk$mk/machine_side"; front = "futuretech:block/$machine/mk$mk/${machine}_front$suffix" }
                }
            }
        }
    }
    Write-Json (Join-Path $assets "blockstates/$machine.json") @{ variants = $variants }
    $cases = @(foreach ($mk in 2..4) {
        @{ when = "$mk"; model = @{ type = 'minecraft:model'; model = "futuretech:block/$machine/mk$mk" } }
    })
    Write-Json (Join-Path $assets "items/$machine.json") @{ model = @{
        type = 'minecraft:select'; property = 'minecraft:block_state'; block_state_property = 'mk'; cases = $cases
        fallback = @{ type = 'minecraft:model'; model = "futuretech:block/$machine" }
    } }
    $lootPath = Join-Path $root "data/futuretech/loot_table/blocks/$machine.json"
    $loot = Get-Content -LiteralPath $lootPath -Raw | ConvertFrom-Json -AsHashtable
    $loot.pools[0].entries[0].functions = @(@{ function = 'minecraft:copy_state'; block = "futuretech:$machine"; properties = @('mk') })
    Write-Json $lootPath $loot
}
$atlasPath = Join-Path $assets 'atlases/blocks.json'
$atlas = Get-Content -LiteralPath $atlasPath -Raw | ConvertFrom-Json -AsHashtable
$atlas.sources = @($atlas.sources | Where-Object { $_.resource -notmatch 'block/machine/mk[234]/' })
foreach ($mk in 2..4) { foreach ($mode in @('input','output','input_output')) {
    $atlas.sources += @{ type = 'single'; resource = "futuretech:block/machine/mk$mk/machine_side_$mode" }
} }
Write-Json $atlasPath $atlas
