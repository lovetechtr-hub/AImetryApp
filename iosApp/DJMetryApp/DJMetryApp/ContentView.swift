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
            .background(Color(red: 11 / 255, green: 18 / 255, blue: 32 / 255))
    }
}
