
import SwiftUI

struct SessionsView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        NavigationStack {
            List(viewModel.sessions) { session in
                VStack(alignment: .leading, spacing: 4) {
                    Text(session.title)
                        .font(.headline)
                    Text(session.status)
                        .foregroundStyle(.secondary)
                    Text(session.updatedAt)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Sessions")
        }
    }
}
