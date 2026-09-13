$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$repository=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$mesh=Get-Content (Join-Path $repository 'build/sphere-preview/mesh.json') -Raw | ConvertFrom-Json
$bitmap=[System.Drawing.Bitmap]::new(512,512)
$g=[System.Drawing.Graphics]::FromImage($bitmap)
try {
    $g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::FromArgb(255,8,15,23))
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,10,41,58))
    try {$g.FillEllipse($brush,66,66,380,380)}finally{$brush.Dispose()}
    $points=@(foreach($node in $mesh.nodes){
        $x=$node[0]*[Math]::Cos(0.45)+$node[2]*[Math]::Sin(0.45)
        $z=$node[2]*[Math]::Cos(0.45)-$node[0]*[Math]::Sin(0.45)
        [pscustomobject]@{X=[single](256+190*$x);Y=[single](256-190*$node[1]);Z=$z}
    })
    foreach($pass in @(@(9,12),@(6,22),@(3,60),@(1,255))) {
        foreach($edge in $mesh.edges){
            $a=$points[$edge[0]];$b=$points[$edge[1]]
            if($a.Z -lt -0.02 -or $b.Z -lt -0.02){continue}
            $pen=[System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb($pass[1],131,229,255),[single]$pass[0])
            try{$g.DrawLine($pen,$a.X,$a.Y,$b.X,$b.Y)}finally{$pen.Dispose()}
        }
    }
    foreach($p in $points){
        if($p.Z -lt 0){continue}
        foreach($pass in @(@(10,20),@(6,50),@(3,255))){
            $brush=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($pass[1],204,247,255))
            try{$g.FillEllipse($brush,[single]($p.X-$pass[0]/2),[single]($p.Y-$pass[0]/2),[single]$pass[0],[single]$pass[0])}finally{$brush.Dispose()}
        }
    }
    $bitmap.Save((Join-Path $PSScriptRoot 'preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
}finally{$g.Dispose();$bitmap.Dispose()}
