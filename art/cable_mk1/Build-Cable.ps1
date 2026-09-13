# Builds every cable asset: the two sprites, the four block models, the item model
# and the blockstate. Written for Windows PowerShell 5.1 so it runs without pwsh.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$assets = Join-Path $repository 'src/main/resources/assets/futuretech'

# ---------------------------------------------------------------- JSON ----
# ConvertTo-Json differs between 5.1 and 7, so we format ourselves and keep the
# two-space style the rest of the mod uses.
$invariant = [Globalization.CultureInfo]::InvariantCulture
function Write-Json($value, $pad) {
    $here = ' ' * $pad
    $inner = ' ' * ($pad + 2)
    if ($value -is [System.Collections.IDictionary]) {
        if ($value.Count -eq 0) { return '{}' }
        $parts = @(foreach ($key in $value.Keys) {
            $inner + '"' + $key + '": ' + (Write-Json $value[$key] ($pad + 2))
        })
        return "{`r`n" + ($parts -join ",`r`n") + "`r`n$here}"
    }
    if ($value -is [object[]]) {
        if ($value.Count -eq 0) { return '[]' }
        $parts = @(foreach ($item in $value) { $inner + (Write-Json $item ($pad + 2)) })
        return "[`r`n" + ($parts -join ",`r`n") + "`r`n$here]"
    }
    if ($value -is [bool]) { if ($value) { return 'true' } else { return 'false' } }
    if ($value -is [double] -or $value -is [single]) { return ([double]$value).ToString('0.0##', $invariant) }
    if ($value -is [int] -or $value -is [long]) { return ([long]$value).ToString($invariant) }
    # Built from character codes so no layer of quoting can eat the escapes.
    $escape = [string][char]92
    $quote = [string][char]34
    $text = ([string]$value).Replace($escape, $escape + $escape).Replace($quote, $escape + $quote)
    return $quote + $text + $quote
}
function Save-Json($relative, $value) {
    $path = Join-Path $assets $relative
    $directory = Split-Path $path -Parent
    if (!(Test-Path $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
    [IO.File]::WriteAllText($path, (Write-Json $value 0) + "`r`n", (New-Object Text.UTF8Encoding $false))
    Write-Host "  $relative"
}

# ------------------------------------------------------------ textures ----
# Same palette and same checker as MK1. The difference is the layout: the strip
# fills the sprite's full height so one copy covers a whole block, and every
# patch is edge-padded so mipmaps never blend the filler into the cable.
$palette = @{
    B = '#255F92'; T = '#3894AF'; C = '#58C4C4'; L = '#79E5D9'
}
foreach ($key in @($palette.Keys)) { $palette[$key] = [Drawing.ColorTranslator]::FromHtml($palette[$key]) }

function Save-Sprite($relative, $size, $rows) {
    $bitmap = [Drawing.Bitmap]::new($size, $size)
    try {
        $height = $rows.Count
        $width = $rows[0].Length
        for ($y = 0; $y -lt $size; $y++) {
            for ($x = 0; $x -lt $size; $x++) {
                # Clamp outside the authored patch: the padding always repeats the
                # nearest real pixel, so bleeding cannot introduce a new colour.
                $sy = [Math]::Min($y, $height - 1)
                $sx = [Math]::Min($x, $width - 1)
                $bitmap.SetPixel($x, $y, $palette[[string]$rows[$sy][$sx]])
            }
        }
        $path = Join-Path $assets $relative
        $bitmap.Save($path, [Drawing.Imaging.ImageFormat]::Png)
        Write-Host "  $relative"
    } finally { $bitmap.Dispose() }
}

# Six texels across the six-unit core; four-texel bands along its length. Thirty-two
# rows are exactly four periods, so consecutive blocks continue the pattern.
$strip = @(for ($y = 0; $y -lt 32; $y++) {
    if ([Math]::Floor($y / 4) % 2 -eq 0) { 'BTCLTB' } else { 'BBLCBB' }
})
Save-Sprite 'textures/block/cable_mk1.png' 32 $strip
# The node's open faces show a small central contact, same as MK1.
Save-Sprite 'textures/block/cable_mk1_node.png' 16 @('TTTBBB', 'TTTBBB', 'TTCLBB', 'BBLCTT', 'BBBTTT', 'BBBTTT')
# The same contact at eight texels, for the plate that closes the cable's mouth inside a
# machine collar. The connector draws it for every tier, so it carries no tier in its name.
Save-Sprite 'textures/block/cable_contact.png' 16 @(
    'TTTTBBBB', 'TTTTBBBB', 'TTTTBBBB', 'TTTCLBBB',
    'BBBLCTTT', 'BBBBTTTT', 'BBBBTTTT', 'BBBBTTTT')

# The two frame materials are hand-painted and nearly flat; copying keeps their subtle
# noise instead of trying to reproduce it.
foreach ($frame in @(@('frame_1_branco.png', 'white'), @('frame_2_cinza.png', 'gray'))) {
    $source = Join-Path $PSScriptRoot ('frame_materials/' + $frame[0])
    $target = 'textures/block/cable_mk1_frame_' + $frame[1] + '.png'
    Copy-Item -LiteralPath $source -Destination (Join-Path $assets $target) -Force
    Write-Host "  $target"
}

# ------------------------------------------------------------ geometry ----
$sideOrder = @('north', 'east', 'south', 'west', 'up', 'down')
$sideAxis = @{ north = 2; south = 2; east = 0; west = 0; up = 1; down = 1 }
$sidePositive = @{ north = $false; south = $true; east = $true; west = $false; up = $true; down = $false }
$opposite = @{ north = 'south'; south = 'north'; east = 'west'; west = 'east'; up = 'down'; down = 'up' }

# A face is dropped when another box in the same model sits flush against it and
# covers it whole. Letting the generator prove this beats hand-listing the inner
# faces, and every quad it removes is one that could never be seen.
function Test-Hidden($part, $side, $parts) {
    $axis = $sideAxis[$side]
    $positive = $sidePositive[$side]
    if ($positive) { $plane = $part.to[$axis] } else { $plane = $part.from[$axis] }
    foreach ($other in $parts) {
        if ($other.id -eq $part.id) { continue }
        if ($positive) { if ($other.from[$axis] -ne $plane) { continue } }
        else { if ($other.to[$axis] -ne $plane) { continue } }
        $covers = $true
        foreach ($o in @(0, 1, 2)) {
            if ($o -eq $axis) { continue }
            if ($other.from[$o] -gt $part.from[$o] -or $other.to[$o] -lt $part.to[$o]) { $covers = $false }
        }
        if ($covers) { return $true }
    }
    return $false
}

function New-Model($parts, $textures, $options) {
    $elements = @(foreach ($part in $parts) {
        $faces = [ordered]@{}
        foreach ($side in $sideOrder) {
            # A run never caps its own ends: both of them always sit against a neighbour
            # that covers them, so those quads could only ever live inside solid geometry.
            if ($options.skipAxis -ge 0 -and $sideAxis[$side] -eq $options.skipAxis) { continue }
            if (Test-Hidden $part $side $parts) { continue }
            if ($part.m -eq 'cable' -or $part.m -eq 'node') {
                $face = [ordered]@{ texture = $options.coreTexture; uv = $options.coreUv }
                # East and west read the strip across the cable's height, not its length.
                if ($options.rotateSides -and ($side -eq 'east' -or $side -eq 'west')) { $face['rotation'] = 90 }
            } else {
                $face = [ordered]@{ texture = "#$($part.m)"; uv = @(0, 0, 16, 16) }
            }
            $faces[$side] = $face
        }
        [ordered]@{
            name  = $part.name
            from  = @([double]$part.from[0], [double]$part.from[1], [double]$part.from[2])
            to    = @([double]$part.to[0], [double]$part.to[1], [double]$part.to[2])
            faces = $faces
        }
    })
    [ordered]@{ ambientocclusion = $false; textures = $textures; elements = $elements }
}

# The cross section shared by the arm and the straight run: four grey corners with
# two white rails flanking each, and the middle of every edge left open so the core
# shows through.
$ring = @(
    @{ x = 4; y = 4; m = 'gray' }, @{ x = 11; y = 4; m = 'gray' },
    @{ x = 4; y = 11; m = 'gray' }, @{ x = 11; y = 11; m = 'gray' },
    @{ x = 5; y = 4; m = 'white' }, @{ x = 10; y = 4; m = 'white' },
    @{ x = 4; y = 5; m = 'white' }, @{ x = 11; y = 5; m = 'white' },
    @{ x = 4; y = 10; m = 'white' }, @{ x = 11; y = 10; m = 'white' },
    @{ x = 5; y = 11; m = 'white' }, @{ x = 10; y = 11; m = 'white' }
)
function New-RunParts($coreEnd, $railEnd) {
    $parts = [Collections.Generic.List[object]]::new()
    $parts.Add(@{ id = 0; name = 'cable'; from = @(5, 5, 0); to = @(11, 11, $coreEnd); m = 'cable' })
    $i = 1
    foreach ($cell in $ring) {
        # Windows PowerShell mis-parses arithmetic inside a hashtable literal on the
        # right of +=, so the corners are worked out before the literal.
        $far = @(($cell.x + 1), ($cell.y + 1), $railEnd)
        $parts.Add(@{ id = $i; name = $cell.m; from = @($cell.x, $cell.y, 0); to = $far; m = $cell.m })
        $i++
    }
    $parts.ToArray()
}

# The node: a 6x6x6 core inside the twelve edges of an 8x8x8 cage.
$nodeEdges = @(
    @(4, 4, 4, 5, 12, 5), @(11, 4, 4, 12, 12, 5), @(4, 4, 11, 5, 12, 12), @(11, 4, 11, 12, 12, 12),
    @(5, 11, 4, 11, 12, 5), @(5, 4, 4, 11, 5, 5), @(5, 11, 11, 11, 12, 12), @(5, 4, 11, 11, 5, 12),
    @(4, 11, 5, 5, 12, 11), @(11, 11, 5, 12, 12, 11), @(4, 4, 5, 5, 5, 11), @(11, 4, 5, 12, 5, 11)
)
$nodeList = [Collections.Generic.List[object]]::new()
$nodeList.Add(@{ id = 0; name = 'cable'; from = @(5, 5, 5); to = @(11, 11, 11); m = 'node' })
$i = 1
foreach ($edge in $nodeEdges) {
    $nodeList.Add(@{ id = $i; name = 'gray'; from = @($edge[0], $edge[1], $edge[2]); to = @($edge[3], $edge[4], $edge[5]); m = 'gray' })
    $i++
}
$nodeParts = $nodeList.ToArray()

# Four bars closing an unconnected face, leaving a 4x4 window onto the contact.
$capRing = @(@(6, 10, 10, 11), @(10, 5, 11, 11), @(6, 5, 10, 6), @(5, 5, 6, 11))
function New-CapParts($side, $startId) {
    $axis = $sideAxis[$side]
    if ($sidePositive[$side]) { $near = 11; $far = 12 } else { $near = 4; $far = 5 }
    $others = @(@(0, 1, 2) | Where-Object { $_ -ne $axis })
    $parts = [Collections.Generic.List[object]]::new()
    $id = $startId
    foreach ($bar in $capRing) {
        $from = @(0, 0, 0); $to = @(0, 0, 0)
        $from[$axis] = $near; $to[$axis] = $far
        $from[$others[0]] = $bar[0]; $from[$others[1]] = $bar[1]
        $to[$others[0]] = $bar[2]; $to[$others[1]] = $bar[3]
        $parts.Add(@{ id = $id; name = 'white'; from = $from; to = $to; m = 'white' })
        $id++
    }
    $parts.ToArray()
}

# -------------------------------------------------------------- models ----
# The frames are flat colours and come straight from frame_materials/.
$textures = [ordered]@{
    cable    = 'futuretech:block/cable_mk1'
    node     = 'futuretech:block/cable_mk1_node'
    white    = 'futuretech:block/cable_mk1_frame_white'
    gray     = 'futuretech:block/cable_mk1_frame_gray'
    particle = 'futuretech:block/cable_mk1_frame_gray'
}
# Two texels per unit everywhere: 32 rows cover a whole block, 10 cover the arm.
$lineOptions = @{ skipAxis = 2; coreTexture = '#cable'; coreUv = @(0, 0, 3, 16); rotateSides = $true }
$armOptions  = @{ skipAxis = 2; coreTexture = '#cable'; coreUv = @(0, 0, 3, 5); rotateSides = $true }
$nodeOptions = @{ skipAxis = -1; coreTexture = '#node'; coreUv = @(0, 0, 6, 6); rotateSides = $false }

Write-Host 'modelos:'
Save-Json 'models/block/cable_mk1_line.json' (New-Model (New-RunParts 16 16) $textures $lineOptions)
Save-Json 'models/block/cable_mk1_arm.json'  (New-Model (New-RunParts 5 4) $textures $armOptions)
Save-Json 'models/block/cable_mk1_node.json' (New-Model $nodeParts $textures $nodeOptions)
Save-Json 'models/block/cable_mk1_cap.json'  (New-Model (New-CapParts 'north' 0) $textures $nodeOptions)

# The item shows the node with every face closed.
$itemList = [Collections.Generic.List[object]]::new()
foreach ($part in $nodeParts) { $itemList.Add($part) }
$capId = 100
foreach ($side in $sideOrder) {
    foreach ($part in (New-CapParts $side $capId)) { $itemList.Add($part) }
    $capId += 10
}
$item = New-Model $itemList.ToArray() $textures $nodeOptions
$item.Remove('ambientocclusion')
$item['parent'] = 'minecraft:block/block'
$item['display'] = [ordered]@{ gui = [ordered]@{
    rotation = @(30, 225, 0); translation = @(0, 0, 0); scale = @(1.1, 1.1, 1.1) } }
Save-Json 'models/item/cable_mk1.json' $item
Save-Json 'items/cable_mk1.json' ([ordered]@{ model = [ordered]@{
    type = 'minecraft:model'; model = 'futuretech:item/cable_mk1' } })

# ---------------------------------------------------------- blockstate ----
# The node is used unless the block is a straight run. Multipart has no NOT, so the
# complement is spelled out: nothing connected, a side whose opposite is open, or
# any two sides that are not opposite.
$nodeWhen = [Collections.Generic.List[object]]::new()
$none = [ordered]@{}
foreach ($side in $sideOrder) { $none[$side] = 'false' }
$nodeWhen.Add($none)
foreach ($side in $sideOrder) {
    $clause = [ordered]@{}
    $clause[$side] = 'true'
    $clause[$opposite[$side]] = 'false'
    $nodeWhen.Add($clause)
}
for ($a = 0; $a -lt $sideOrder.Count; $a++) {
    for ($b = $a + 1; $b -lt $sideOrder.Count; $b++) {
        if ($opposite[$sideOrder[$a]] -eq $sideOrder[$b]) { continue }
        $clause = [ordered]@{}
        $clause[$sideOrder[$a]] = 'true'
        $clause[$sideOrder[$b]] = 'true'
        $nodeWhen.Add($clause)
    }
}
$nodeCondition = [ordered]@{ OR = $nodeWhen.ToArray() }
$turn = @{ north = @{}; east = @{ y = 90 }; south = @{ y = 180 }; west = @{ y = 270 }; up = @{ x = 270 }; down = @{ x = 90 } }
function New-Apply($model, $rotation) {
    $apply = [ordered]@{ model = "futuretech:block/$model" }
    if ($rotation.ContainsKey('x')) { $apply['x'] = [int]$rotation['x'] }
    if ($rotation.ContainsKey('y')) { $apply['y'] = [int]$rotation['y'] }
    $apply
}
$multipart = [Collections.Generic.List[object]]::new()
$multipart.Add([ordered]@{ when = $nodeCondition; apply = (New-Apply 'cable_mk1_node' @{}) })
foreach ($side in $sideOrder) {
    $closed = [ordered]@{}; $closed[$side] = 'false'
    $multipart.Add([ordered]@{ when = [ordered]@{ AND = @($nodeCondition, $closed) }; apply = (New-Apply 'cable_mk1_cap' $turn[$side]) })
}
foreach ($side in $sideOrder) {
    $open = [ordered]@{}; $open[$side] = 'true'
    $multipart.Add([ordered]@{ when = [ordered]@{ AND = @($nodeCondition, $open) }; apply = (New-Apply 'cable_mk1_arm' $turn[$side]) })
}
foreach ($run in @(@{ a = 'north'; b = 'south'; r = @{} }, @{ a = 'east'; b = 'west'; r = @{ y = 90 } }, @{ a = 'up'; b = 'down'; r = @{ x = 270 } })) {
    $clause = [ordered]@{}
    foreach ($side in $sideOrder) {
        if ($side -eq $run.a -or $side -eq $run.b) { $clause[$side] = 'true' } else { $clause[$side] = 'false' }
    }
    $multipart.Add([ordered]@{ when = $clause; apply = (New-Apply 'cable_mk1_line' $run.r) })
}
Save-Json 'blockstates/cable_mk1.json' ([ordered]@{ multipart = $multipart.ToArray() })
Write-Host 'pronto.'
