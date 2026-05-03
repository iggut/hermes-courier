import urllib.request
import json

data = json.dumps({
    'pr_title': 'Task Cancelled',
    'pr_body': 'Optimization already implemented.'
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
