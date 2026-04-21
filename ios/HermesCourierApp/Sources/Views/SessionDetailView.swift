import SwiftUI

struct SessionDetailView: View {
    let session: HermesSessionSummary
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(session.title)
                    .font(.title2)
                    .fontWeight(.semibold)
                Text(session.status)
                Text(session.updatedAt)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Session ID: \(session.sessionId)")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                sessionControlSection

                Button("Refresh gateway") {
                    Task { await viewModel.refresh() }
                }
                .buttonStyle(.borderedProminent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .navigationTitle("Session")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var sessionControlSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Session control")
                .font(.headline)
            Text("Pause, resume, or terminate via gateway session-control endpoints.")
                .font(.caption)
                .foregroundStyle(.secondary)
            VStack(spacing: 8) {
                Button("Pause session") {
                    viewModel.submitSessionControlAction(sessionId: session.sessionId, action: "pause")
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity, alignment: .leading)
                Button("Resume session") {
                    viewModel.submitSessionControlAction(sessionId: session.sessionId, action: "resume")
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity, alignment: .leading)
                Button("Terminate session", role: .destructive) {
                    viewModel.submitSessionControlAction(sessionId: session.sessionId, action: "terminate")
                }
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Text(viewModel.sessionControlStatus)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct SessionDetailContainerView: View {
    let sessionId: String
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        Group {
            if let session = viewModel.sessions.first(where: { $0.sessionId == sessionId }) {
                SessionDetailView(session: session)
            } else if viewModel.sessionDetailLoadError != nil {
                VStack(alignment: .leading, spacing: 12) {
                    Text(viewModel.sessionDetailLoadError ?? "Unable to load session")
                    Button("Retry") {
                        viewModel.loadSessionDetailIfMissing(sessionId: sessionId)
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Refresh gateway") {
                        Task { await viewModel.refresh() }
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
            } else {
                ProgressView("Loading session…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("Session")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: sessionId) {
            viewModel.loadSessionDetailIfMissing(sessionId: sessionId)
        }
    }
}
