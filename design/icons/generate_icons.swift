// Генерирует иконки приложения DJMetry из вектора знака «#».
// Запуск из корня проекта: swift design/icons/generate_icons.swift
import AppKit
import CoreGraphics

// Знак «#» из design/brand/djmetry_logo.svg (только M/L/Z-команды)
let hashPath = "M442.383 269.261L430.918 335.892L498.746 338.906L502.601 425.638L416.425 421.808L401.908 507.168L312.953 503.215L327.47 417.854L234.622 413.728L220.105 499.088L131.705 495.159L146.223 409.799L65.0505 406.191L61.1958 319.46L160.715 323.883L172.18 257.252L93.2322 253.744L89.3774 167.012L187.229 171.361L201.264 87.6437L289.664 91.5725L275.628 175.29L368.476 179.416L382.511 95.6991L471.467 99.6527L457.431 183.37L526.928 186.459L530.783 273.19L442.383 269.261ZM353.428 265.308L260.58 261.181L249.115 327.812L341.962 331.938L353.428 265.308Z"

let green = CGColor(red: 0x5E / 255.0, green: 0xE6 / 255.0, blue: 0xA8 / 255.0, alpha: 1)
let navy = CGColor(red: 0x0B / 255.0, green: 0x12 / 255.0, blue: 0x20 / 255.0, alpha: 1)
let white = CGColor(red: 1, green: 1, blue: 1, alpha: 1)

func parse(_ d: String) -> CGPath {
    let path = CGMutablePath()
    let scanner = Scanner(string: d)
    scanner.charactersToBeSkipped = .whitespaces
    var cmd: Character = "M"
    while !scanner.isAtEnd {
        if let c = scanner.scanCharacter(), "MLZ".contains(c) {
            cmd = c
            if c == "Z" { path.closeSubpath(); continue }
        } else {
            scanner.currentIndex = scanner.string.index(before: scanner.currentIndex)
        }
        guard let x = scanner.scanDouble(), let y = scanner.scanDouble() else { break }
        if cmd == "M" { path.move(to: CGPoint(x: x, y: y)); cmd = "L" } else { path.addLine(to: CGPoint(x: x, y: y)) }
    }
    return path
}

let hash = parse(hashPath)
let bounds = hash.boundingBoxOfPath

/// size — сторона PNG, markWidth — доля ширины, которую занимает знак.
func render(size: Int, markWidth: CGFloat, background: CGColor?, mark: CGColor, round: Bool = false, to file: String) {
    let s = CGFloat(size)
    let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpace(name: CGColorSpace.sRGB)!,
                        bitmapInfo: (background != nil && !round ? CGImageAlphaInfo.noneSkipLast : CGImageAlphaInfo.premultipliedLast).rawValue)!
    // Непрозрачные иконки — без альфа-канала (требование App Store)
    if let bg = background {
        ctx.setFillColor(bg)
        if round { ctx.fillEllipse(in: CGRect(x: 0, y: 0, width: s, height: s)) } else { ctx.fill(CGRect(x: 0, y: 0, width: s, height: s)) }
    }
    let scale = s * markWidth / bounds.width
    // SVG: ось Y вниз, CoreGraphics: вверх
    ctx.translateBy(x: s / 2, y: s / 2)
    ctx.scaleBy(x: scale, y: -scale)
    ctx.translateBy(x: -bounds.midX, y: -bounds.midY)
    ctx.addPath(hash)
    ctx.setFillColor(mark)
    ctx.fillPath(using: .evenOdd)
    let url = URL(fileURLWithPath: file)
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
    try! rep.representation(using: .png, properties: [:])!.write(to: url)
    print("✓ \(file)")
}

let ios = "iosApp/DJMetryApp/DJMetryApp/Assets.xcassets/AppIcon.appiconset"
let res = "androidApp/src/main/res"
let store = "design/icons/store"
let markWidth: CGFloat = 0.58

// iOS: обычная, тёмная (фон рисует система) и тонированная (система красит белый знак)
render(size: 1024, markWidth: markWidth, background: navy, mark: green, to: "\(ios)/icon-1024.png")
render(size: 1024, markWidth: markWidth, background: nil, mark: green, to: "\(ios)/icon-1024-dark.png")
render(size: 1024, markWidth: markWidth, background: nil, mark: white, to: "\(ios)/icon-1024-tinted.png")

// Android: PNG для API < 26 (на 26+ используется адаптивная векторная иконка)
for (dir, px) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    render(size: px, markWidth: markWidth, background: navy, mark: green, to: "\(res)/mipmap-\(dir)/ic_launcher.png")
    render(size: px, markWidth: 0.52, background: navy, mark: green, round: true, to: "\(res)/mipmap-\(dir)/ic_launcher_round.png")
}

// Магазины
render(size: 1024, markWidth: markWidth, background: navy, mark: green, to: "\(store)/app-store-1024.png")
render(size: 512, markWidth: markWidth, background: navy, mark: green, to: "\(store)/google-play-512.png")

// Десктоп: скруглённый квадрат с отступами (сетка иконок macOS: плитка 824 из 1024, радиус ~185)
func renderDesktop(size: Int, to file: String) {
    let s = CGFloat(size)
    let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpace(name: CGColorSpace.sRGB)!,
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    let tile = s * 824 / 1024
    let rect = CGRect(x: (s - tile) / 2, y: (s - tile) / 2, width: tile, height: tile)
    ctx.addPath(CGPath(roundedRect: rect, cornerWidth: tile * 0.225, cornerHeight: tile * 0.225, transform: nil))
    ctx.setFillColor(navy)
    ctx.fillPath()
    let scale = tile * 0.56 / bounds.width
    ctx.translateBy(x: s / 2, y: s / 2)
    ctx.scaleBy(x: scale, y: -scale)
    ctx.translateBy(x: -bounds.midX, y: -bounds.midY)
    ctx.addPath(hash)
    ctx.setFillColor(green)
    ctx.fillPath(using: .evenOdd)
    let url = URL(fileURLWithPath: file)
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! NSBitmapImageRep(cgImage: ctx.makeImage()!).representation(using: .png, properties: [:])!.write(to: url)
    print("✓ \(file)")
}

let iconset = "desktopApp/icons/djmetry.iconset"
for (name, px) in [("16x16", 16), ("16x16@2x", 32), ("32x32", 32), ("32x32@2x", 64), ("128x128", 128),
                   ("128x128@2x", 256), ("256x256", 256), ("256x256@2x", 512), ("512x512", 512), ("512x512@2x", 1024)] {
    renderDesktop(size: px, to: "\(iconset)/icon_\(name).png")
}
renderDesktop(size: 512, to: "desktopApp/icons/djmetry.png")
renderDesktop(size: 256, to: "desktopApp/src/main/resources/djmetry.png")
for px in [16, 24, 32, 48, 64, 128, 256] {
    renderDesktop(size: px, to: "desktopApp/icons/ico/\(px).png")
}
// Дальше: iconutil → djmetry.icns, design/icons/make_ico.py → djmetry.ico (см. design/README.md)
