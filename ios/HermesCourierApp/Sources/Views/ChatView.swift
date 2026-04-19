
import SwiftUI

struct ChatView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var draft = ""

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(viewModel.messages) { message in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(message.sender)
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
            HStack(spacing: 12) {
                TextField("Message Hermes", text: $draft)
                    .textFieldStyle(.roundedBorder)
                Button("Send") {
                    draft = ""
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .background(.ultraThinMaterial)
        }
        .navigationTitle("Chat")
    }
}
