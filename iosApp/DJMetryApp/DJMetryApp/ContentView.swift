import SwiftUI
import Shared

/// Весь интерфейс — общий Compose Multiplatform из модуля shared (MainViewController в Main.kt).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            // Universal Link (https://djmetry.com/artist/…, /booking/…) — тот же маршрут, что у пуша
            .onOpenURL { url in
                // Только наш сайт: остальные https-ссылки сюда прийти не должны (applinks:djmetry.com)
                guard url.scheme == "https", let host = url.host?.lowercased(),
                      host == "djmetry.com" || host.hasSuffix(".djmetry.com") else { return }
                // Только страницы, для которых есть экран: артист и заявка букинга (остальное — сайт)
                guard url.path.hasPrefix("/artist/") || url.path.hasPrefix("/booking/requests/") else { return }
                PushTokens.shared.onNotificationOpened(url: url.absoluteString)
            }
            .background(Color(red: 11 / 255, green: 18 / 255, blue: 32 / 255))
    }
}
