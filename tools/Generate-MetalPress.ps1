$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$assets = Join-Path $root 'src/main/resources/assets/futuretech'
function Write-Json($path, $value) {
    [IO.Directory]::CreateDirectory((Split-Path $path -Parent)) | Out-Null
    [IO.File]::WriteAllText($path, ($value | ConvertTo-Json -Depth 40) + "`n", [Text.UTF8Encoding]::new($false))
}
# PNG front textures share the casing and corner trim used by the other machines.
$variants = [ordered]@{}
$cases = @()
foreach ($mk in 1..4) {
    $side = if ($mk -eq 1) { 'futuretech:block/machine/machine_side' } else { "futuretech:block/machine/mk$mk/machine_side" }
    foreach ($lit in @($false, $true)) {
        $suffix = if ($lit) { '_on' } else { '' }
        $name = "metal_press/mk$mk$suffix"
        $script:parts = [Collections.Generic.List[object]]::new()
        $faces = [ordered]@{}
        foreach ($face in @('north','south','west','east','up','down')) {
            $faces[$face] = @{ texture='#side'; uv=@(0,0,16,16); cullface=$face }
        }
        $parts.Add(@{ from=@(0,0,0); to=@(16,16,16); faces=$faces })
        $faces.north.texture = '#front'
        # The new front is a PNG. Only the existing MK corner trim is layered over it.
        if ($mk -gt 1) {
            foreach ($left in @($true, $false)) {
                foreach ($top in @($true, $false)) {
                    $u = if ($left) { 0.0 } else { 13.5 }
                    $v = if ($top) { 0.0 } else { 15.5 }
                    $rectangles = @(,@($u,$v,($u+2.5),($v+0.5)))
                    $u = if ($left) { 0.0 } else { 15.5 }
                    $v = if ($top) { 0.5 } else { 13.5 }
                    $rectangles += ,@($u,$v,($u+0.5),($v+2.0))
                    foreach ($uv in $rectangles) {
                        $parts.Add(@{ from=@((16-$uv[2]),(16-$uv[3]),-0.001); to=@((16-$uv[0]),(16-$uv[1]),-0.001)
                            faces=@{ north=@{ texture='#side'; uv=$uv; cullface='north' } } })
                    }
                }
            }
        }
        $model = @{ parent='minecraft:block/block'; textures=@{
            particle=$side; side=$side; front="futuretech:block/metal_press/metal_press_front$suffix"
        }; elements=$parts.ToArray() }
        Write-Json "$assets/models/block/$name.json" $model
        $rotation=0
        foreach ($facing in @('north','east','south','west')) {
            $variants["facing=$facing,lit=$($lit.ToString().ToLower()),mk=$mk"] = @{ model="futuretech:block/$name"; y=$rotation }
            $rotation += 90
        }
    }
    if ($mk -gt 1) { $cases += @{ when="$mk"; model=@{ type='minecraft:model'; model="futuretech:block/metal_press/mk$mk" } } }
}
Write-Json "$assets/blockstates/metal_press.json" @{ variants=$variants }
Write-Json "$assets/items/metal_press.json" @{ model=@{ type='minecraft:select'; property='minecraft:block_state'; block_state_property='mk'; cases=$cases; fallback=@{ type='minecraft:model'; model='futuretech:block/metal_press/mk1' } } }

