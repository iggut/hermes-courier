import urllib.request
import json

data = json.dumps({
    'pr_title': '⚡ Optimize AppViewModel boot performance with async let',
    'pr_body': '💡 What: Updated attemptLiveConnection and attemptFallbackConnection methods to use `async let` for network requests (Dashboard, Sessions, Approvals, Conversation) rather than awaiting them sequentially.\n🎯 Why: In the original implementation, the `fetch` calls block each other sequentially. Since they have no direct dependencies on each other, they can be fetched concurrently via a TaskGroup/async let logic. This effectively cuts down latency to the longest of the individual network calls instead of the sum of their durations.\n📊 Measured Improvement: Due to the limits of the Linux testing environment where macOS native features and `xcodebuild` aren\'t accessible natively without a simulator, an automatic measure was unable to run. Nevertheless, changing N sequential network calls with average time T to concurrent network requests reduces theoretical waiting time from N*T down to ~T.'
}).encode('utf-8')

req = urllib.request.Request(
    'http://localhost:8000/v1/tools/submit',
    data=data,
    headers={'Content-Type': 'application/json'}
)

try:
    print(urllib.request.urlopen(req).read().decode('utf-8'))
except Exception as e:
    print(e)
