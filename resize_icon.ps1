Add-Type -AssemblyName System.Drawing

$source = 'C:\cosmos_54\COSMOS\assets\icon.png'
$outputBase = 'C:\COSMOS_ANDROID\app\src\main\res'

$sizes = @{
    'mdpi' = 48
    'hdpi' = 72
    'xhdpi' = 96
    'xxhdpi' = 144
    'xxxhdpi' = 192
}

$originalImage = [System.Drawing.Image]::FromFile($source)
Write-Host "Original image size: $($originalImage.Width)x$($originalImage.Height)"

foreach ($density in $sizes.Keys) {
    $size = $sizes[$density]
    $outputDir = Join-Path $outputBase "mipmap-$density"
    $outputPath = Join-Path $outputDir 'ic_launcher.png'

    $resized = New-Object System.Drawing.Bitmap($size, $size)
    $graphics = [System.Drawing.Graphics]::FromImage($resized)
    $graphics.DrawImage($originalImage, 0, 0, $size, $size)
    $graphics.Dispose()

    $resized.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $resized.Dispose()
    Write-Host "Created $density at ${size}x${size}"
}

$originalImage.Dispose()
Write-Host 'Icon resizing complete'
