
import SwiftUI

struct ChatView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var draft = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.messages) { message in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(message.author)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(message.body)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                }
                .padding()
            }
            .safeAreaInset(edge: .bottom) {
                VStack(alignment: .leading, spacing: 8) {
                    if let err = viewModel.conversationActionError, !err.isEmpty {
                        Text(err)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                    HStack(spacing: 12) {
                        TextField("Message Hermes", text: $draft)
                            .textFieldStyle(.roundedBorder)
                        Button("Send") {
                            viewModel.sendConversationMessage(draft)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.conversationActionState == .sending)
                    }
                    .onChange(of: viewModel.conversationActionState) { _, newState in
                        if newState == .sent {
                            draft = ""
                        }
                    }
                    if viewModel.conversationActionState == .sending {
                        ProgressView()
                    }
                    Text(viewModel.conversationActionStatus)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .padding()
                .background(.ultraThinMaterial)
            }
            .navigationTitle("Chat")
        }
    }
}
