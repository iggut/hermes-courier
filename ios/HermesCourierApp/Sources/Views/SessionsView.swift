import SwiftUI

struct SessionsView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Browse active and historical Hermes runs.")
                }

                ForEach(viewModel.sessions) { session in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(session.title).font(.headline)
                        Text(session.status)
                        Text(session.updatedAt).font(.caption).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }

                if viewModel.sessions.isEmpty {
                    Text("No sessions yet. After you refresh from the dashboard or settings, new gateway activity appears here.")
                }
            }
            .navigationTitle("Sessions")
        }
    }
}
