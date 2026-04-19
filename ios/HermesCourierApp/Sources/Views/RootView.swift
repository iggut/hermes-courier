
import SwiftUI

enum HermesTab: String, CaseIterable {
    case dashboard = "Dashboard"
    case chat = "Chat"
    case approvals = "Approvals"
    case sessions = "Sessions"
    case settings = "Settings"
}

struct RootView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var selectedTab: HermesTab = .dashboard

    var body: some View {
        TabView(selection: $selectedTab) {
            DashboardView()
                .tabItem { Label("Dashboard", systemImage: "house") }
                .tag(HermesTab.dashboard)

            ChatView()
                .tabItem { Label("Chat", systemImage: "message") }
                .tag(HermesTab.chat)

            ApprovalsView()
                .tabItem { Label("Approvals", systemImage: "checkmark.seal") }
                .tag(HermesTab.approvals)

            SessionsView()
                .tabItem { Label("Sessions", systemImage: "rectangle.stack") }
                .tag(HermesTab.sessions)

            SettingsView()
                .tabItem { Label("Settings", systemImage: "gear") }
                .tag(HermesTab.settings)
        }
        .tint(.blue)
    }
}
