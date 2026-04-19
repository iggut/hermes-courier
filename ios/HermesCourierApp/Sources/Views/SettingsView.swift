
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        NavigationStack {
            Form {
                Section("Connection") {
                    Text(viewModel.dashboard.connectionState)
                }
                Section("Security") {
                    Text(viewModel.authStatus)
                }
                Section("Notifications") {
                    Text("Approvals, alerts, and completion updates")
                }
            }
            .navigationTitle("Settings")
        }
    }
}
