"""Wait for the complete local stack, including a real OIDC password grant."""
import json
import os
import time
import urllib.parse
import urllib.request

gateway = os.environ.get("SERVICE_URL", "http://localhost:38080")
identity = os.environ.get("IDENTITY_URL", "http://localhost:38090")
urls = [
    gateway + "/manage/health",
    "http://localhost:38050/manage/health",
    "http://localhost:38060/manage/health",
    "http://localhost:38070/manage/health",
    identity + "/realms/library/.well-known/openid-configuration",
]
form = urllib.parse.urlencode({
    "grant_type": "password", "scope": "openid profile email",
    "username": "testmax", "password": "lab5-local-password",
    "clientId": "library-client", "clientSecret": "lab5-local-client-secret",
}).encode()
deadline = time.monotonic() + 240
while time.monotonic() < deadline:
    try:
        for url in urls:
            with urllib.request.urlopen(url, timeout=5) as response:
                assert response.status == 200
        with urllib.request.urlopen(gateway + "/api/v1/authorize", data=form, timeout=5) as response:
            token = json.load(response)["access_token"]
        request = urllib.request.Request(gateway + "/api/v1/rating",
                                         headers={"Authorization": "Bearer " + token})
        with urllib.request.urlopen(request, timeout=5) as response:
            assert response.status == 200
        print("All services and OIDC token validation are ready.")
        break
    except Exception:
        time.sleep(3)
else:
    raise SystemExit("Services did not become ready within 240 seconds.")
