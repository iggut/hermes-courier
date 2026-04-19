import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Dashboard", systemImage: "rectangle.grid.2x2") }

            SessionsView()
                .tabItem { Label("Sessions", systemImage: "clock.arrow.circlepath") }

            ApprovalsView()
                .tabItem { Label("Approvals", systemImage: "checkmark.shield") }

            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }
}
