$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$repository=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$bitmap=[System.Drawing.Bitmap]::new(32,32)
$dark=[System.Drawing.ColorTranslator]::FromHtml('#202829')
$sideBorder=[System.Drawing.ColorTranslator]::FromHtml('#2C333B')
$edgeLight=[System.Drawing.ColorTranslator]::FromHtml('#687582')
$sideSteel=[System.Drawing.ColorTranslator]::FromHtml('#505C68')
$sideShade=[System.Drawing.ColorTranslator]::FromHtml('#424D58')
$fastener=[System.Drawing.ColorTranslator]::FromHtml('#8A959F')
try {
    for($y=0;$y -lt 32;$y++){for($x=0;$x -lt 32;$x++){
        $color=$dark
        if($x -ge 7 -and $x -lt 25 -and $y -ge 7 -and $y -lt 25){
            $edge=[Math]::Min([Math]::Min($x-7,24-$x),[Math]::Min($y-7,24-$y))
            # Match the approved side panels on both front ledges as well.
            $color=if($edge -eq 0){$sideBorder}elseif($edge -eq 1){$edgeLight}elseif($edge -eq 2){$sideSteel}else{$sideShade}
            if($x -in @(9,22) -and $y -in @(9,22)){$color=$fastener}
        }
        $bitmap.SetPixel($x,$y,$color)
    }}
    # Broad graphite steel panel with a shallow bevel, using the machine palette.
    # Adjacent midtone rows form one plate rather than alternating bright stripes.
    $bands=@($sideBorder,$edgeLight,$sideSteel,$sideSteel,$sideShade,$sideBorder)
    for($y=0;$y -lt 6;$y++){for($x=0;$x -lt 32;$x++){
        $color=$bands[$y]
        if($x -in @(9,22) -and $y -eq 2){$color=$fastener}
        if($x -in @(9,22) -and $y -eq 3){$color=$sideBorder}
        $bitmap.SetPixel($x,$y,$color)
    }}
    # The neck uses the same single recessed panel, with two small screw heads.
    $neckBands=@($sideBorder,$edgeLight,$sideSteel,$sideSteel,$sideShade,$sideBorder)
    for($y=0;$y -lt 6;$y++){for($x=0;$x -lt 32;$x++){
        $color=$neckBands[$y]
        if($x -in @(10,21) -and $y -eq 2){$color=$fastener}
        if($x -in @(10,21) -and $y -eq 3){$color=$sideBorder}
        $bitmap.SetPixel($x,26+$y,$color)
    }}
    $bitmap.Save((Join-Path $repository 'src/main/resources/assets/futuretech/textures/block/cable_connector.png'),[System.Drawing.Imaging.ImageFormat]::Png)
}finally{$bitmap.Dispose()}

# Render the exact Java collar dimensions with the same cap/side material mapping.
$source=Get-Content (Join-Path $repository 'src/main/java/dev/futuretech/block/CableConnector.java') -Raw
$elements=@(foreach($match in [regex]::Matches($source,'new Box\(([\d.F,]+),(true|false)\)')){
    $p=@($match.Groups[1].Value.Split(',') | ForEach-Object { [double]::Parse($_.Replace('F',''),[cultureinfo]::InvariantCulture) })
    $flange=$match.Groups[2].Value -eq 'true'
    $faces=@{}
    foreach($side in @('up','down','east','west','north','south')){
        $v0=if($flange){0}else{13}
        $v1=if($flange){3}else{16}
        $uv=if($side -in @('north','south')){@($p[0],$p[1],$p[3],$p[4])}else{
            if($side -in @('up','down')){@($p[0],$v0,$p[3],$v1)}else{@($p[1],$v0,$p[4],$v1)}
        }
        $faces[$side]=@{texture='#connector';uv=$uv}
        if($side -in @('east','west')){$faces[$side].rotation=90}
    }
    @{from=@($p[0],$p[1],$p[2]);to=@($p[3],$p[4],$p[5]);faces=$faces}
})
if($elements.Count -ne 8){throw 'Connector geometry could not be exported'}
@{elements=$elements} | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $PSScriptRoot 'connector-preview-model.json') -Encoding utf8
