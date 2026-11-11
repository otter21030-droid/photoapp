Add-Type -AssemblyName System.Drawing

function Create-RoundedRectanglePath {
    param(
        [System.Drawing.RectangleF]$rect,
        [float]$radius
    )
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $radius * 2

    # Arc top-left
    $path.AddArc($rect.X, $rect.Y, $diameter, $diameter, 180, 90)
    # Line top
    $path.AddLine($rect.X + $radius, $rect.Y, $rect.Right - $radius, $rect.Y)
    # Arc top-right
    $path.AddArc($rect.Right - $diameter, $rect.Y, $diameter, $diameter, 270, 90)
    # Line right
    $path.AddLine($rect.Right, $rect.Y + $radius, $rect.Right, $rect.Bottom - $radius)
    # Arc bottom-right
    $path.AddArc($rect.Right - $diameter, $rect.Bottom - $diameter, $diameter, $diameter, 0, 90)
    # Line bottom
    $path.AddLine($rect.Right - $radius, $rect.Bottom, $rect.X + $radius, $rect.Bottom)
    # Arc bottom-left
    $path.AddArc($rect.X, $rect.Bottom - $diameter, $diameter, $diameter, 90, 90)
    # Line left
    $path.AddLine($rect.X, $rect.Bottom - $radius, $rect.X, $rect.Y + $radius)
    $path.CloseFigure()
    return $path
}

function Draw-FrameFitIcon {
    param(
        [int]$size,
        [bool]$isRound,
        [string]$outputPath
    )

    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $scale = $size / 100.0

    # 1. Background Shape & Gradient
    $bgRect = New-Object System.Drawing.RectangleF(0, 0, $size, $size)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.PointF(0, 0)),
        (New-Object System.Drawing.PointF($size, $size)),
        ([System.Drawing.ColorTranslator]::FromHtml("#FF5E7E")),
        ([System.Drawing.ColorTranslator]::FromHtml("#2B86C5"))
    )
    # Add color blend for 3-stop gradient (Pink -> Purple -> Blue)
    $cb = New-Object System.Drawing.Drawing2D.ColorBlend
    $cb.Colors = @(
        ([System.Drawing.ColorTranslator]::FromHtml("#FF5277")),
        ([System.Drawing.ColorTranslator]::FromHtml("#7D47A6")),
        ([System.Drawing.ColorTranslator]::FromHtml("#2872BD"))
    )
    $cb.Positions = @(0.0, 0.5, 1.0)
    $brush.InterpolationColors = $cb

    if ($isRound) {
        $bgPath = New-Object System.Drawing.Drawing2D.GraphicsPath
        $bgPath.AddEllipse(1 * $scale, 1 * $scale, ($size - 2 * $scale), ($size - 2 * $scale))
        $g.FillPath($brush, $bgPath)
    } else {
        $radius = 22.0 * $scale
        $roundBox = New-Object System.Drawing.RectangleF(0, 0, $size, $size)
        $bgPath = Create-RoundedRectanglePath $roundBox $radius
        $g.FillPath($brush, $bgPath)
    }

    # 2. Translucent Glass Photo Frame Card
    $cardWidth = 56.0 * $scale
    $cardHeight = 44.0 * $scale
    $cardX = (100.0 * $scale - $cardWidth) / 2.0
    $cardY = (100.0 * $scale - $cardHeight) / 2.0
    $cardRect = New-Object System.Drawing.RectangleF($cardX, $cardY, $cardWidth, $cardHeight)
    $cardPath = Create-RoundedRectanglePath $cardRect (9.0 * $scale)

    $cardFill = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(45, 255, 255, 255))
    $g.FillPath($cardFill, $cardPath)

    $cardBorderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(210, 255, 255, 255), [Math]::Max(1.0, (2.6 * $scale)))
    $g.DrawPath($cardBorderPen, $cardPath)

    # 3. Viewfinder Crop Anchors (Golden Yellow)
    $anchorPen = New-Object System.Drawing.Pen([System.Drawing.ColorTranslator]::FromHtml("#FFEAA7"), [Math]::Max(1.5, (3.2 * $scale)))
    $anchorPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $anchorPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

    $aLen = 8.0 * $scale
    $ox1 = $cardX - (4.0 * $scale)
    $oy1 = $cardY - (4.0 * $scale)
    $ox2 = $cardX + $cardWidth + (4.0 * $scale)
    $oy2 = $cardY + $cardHeight + (4.0 * $scale)

    # Top-Left
    $g.DrawLine($anchorPen, $ox1, $oy1 + $aLen, $ox1, $oy1)
    $g.DrawLine($anchorPen, $ox1, $oy1, $ox1 + $aLen, $oy1)
    # Top-Right
    $g.DrawLine($anchorPen, $ox2 - $aLen, $oy1, $ox2, $oy1)
    $g.DrawLine($anchorPen, $ox2, $oy1, $ox2, $oy1 + $aLen)
    # Bottom-Left
    $g.DrawLine($anchorPen, $ox1, $oy2 - $aLen, $ox1, $oy2)
    $g.DrawLine($anchorPen, $ox1, $oy2, $ox1 + $aLen, $oy2)
    # Bottom-Right
    $g.DrawLine($anchorPen, $ox2 - $aLen, $oy2, $ox2, $oy2)
    $g.DrawLine($anchorPen, $ox2, $oy2, $ox2, $oy2 - $aLen)

    # 4. Center Camera Lens
    $cx = 50.0 * $scale
    $cy = 50.0 * $scale
    $lensR1 = 12.0 * $scale
    $lensPen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, [Math]::Max(1.0, (2.5 * $scale)))
    $g.DrawEllipse($lensPen, $cx - $lensR1, $cy - $lensR1, $lensR1 * 2, $lensR1 * 2)

    $lensR2 = 8.0 * $scale
    $lensFill = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $g.FillEllipse($lensFill, $cx - $lensR2, $cy - $lensR2, $lensR2 * 2, $lensR2 * 2)

    $lensR3 = 4.0 * $scale
    $lensCoreFill = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#FF5277"))
    $g.FillEllipse($lensCoreFill, $cx - $lensR3, $cy - $lensR3, $lensR3 * 2, $lensR3 * 2)

    # 5. Magic Sparkling Star (Top-Right of lens)
    $starX = 66.0 * $scale
    $starY = 34.0 * $scale
    $starR = 6.0 * $scale
    $starPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $starPath.AddBezier($starX, $starY - $starR, $starX, $starY, $starX, $starY, $starX + $starR, $starY)
    $starPath.AddBezier($starX + $starR, $starY, $starX, $starY, $starX, $starY, $starX, $starY + $starR)
    $starPath.AddBezier($starX, $starY + $starR, $starX, $starY, $starX, $starY, $starX - $starR, $starY)
    $starPath.AddBezier($starX - $starR, $starY, $starX, $starY, $starX, $starY, $starX, $starY - $starR)
    $starPath.CloseFigure()
    $starBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#FFEAA7"))
    $g.FillPath($starBrush, $starPath)

    # Small Dot Sparkle
    $g.FillEllipse($lensFill, (35.0 * $scale), (62.0 * $scale), (2.2 * $scale), (2.2 * $scale))

    # Save PNG
    $bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
    Write-Host "Generated: $outputPath"
}

$baseRes = "C:\Users\Hss\.gemini\antigravity\scratch\framefit-android\app\src\main\res"
$sizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($entry in $sizes.GetEnumerator()) {
    $dir = Join-Path $baseRes $entry.Key
    $outSquare = Join-Path $dir "ic_launcher.png"
    $outRound = Join-Path $dir "ic_launcher_round.png"
    Draw-FrameFitIcon -size $entry.Value -isRound $false -outputPath $outSquare
    Draw-FrameFitIcon -size $entry.Value -isRound $true -outputPath $outRound
}

# 512x512 Play Store Icon
$playStoreIcon = "C:\Users\Hss\.gemini\antigravity\scratch\framefit-android\app\src\main\ic_launcher-playstore.png"
Draw-FrameFitIcon -size 512 -isRound $false -outputPath $playStoreIcon

