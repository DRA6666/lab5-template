"""Exercise real Keycloak tokens against each service, including user isolation."""
import json
import urllib.error
import urllib.parse
import urllib.request

def call(url, token=None, form=None, username=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if username:
        headers["X-User-Name"] = username
    data = urllib.parse.urlencode(form).encode() if form else None
    request = urllib.request.Request(url, headers=headers, data=data)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()

gateway = "http://localhost:38080"
credentials = {
    "grant_type": "password", "scope": "openid profile email",
    "username": "testmax", "password": "lab5-local-password",
    "clientId": "library-client", "clientSecret": "lab5-local-client-secret",
}
status, body = call(gateway + "/api/v1/authorize", form=credentials)
assert status == 200, (status, body)
token = json.loads(body)["access_token"]
for url in (
    gateway + "/api/v1/rating",
    "http://localhost:38050/api/v1/rating",
    "http://localhost:38060/api/v1/libraries?city=Moscow",
    "http://localhost:38070/api/v1/reservations",
):
    assert call(url)[0] == 401, url
    assert call(url, username="forged-user")[0] == 401, url
    assert call(url, token="invalid.jwt.token")[0] == 401, url
    valid_status, valid_body = call(url, token=token)
    assert valid_status == 200, (url, valid_status, valid_body)
    forged_status, forged_body = call(url, token=token, username="forged-user")
    assert (forged_status, forged_body) == (valid_status, valid_body), url
credentials["password"] = "wrong-password"
assert call(gateway + "/api/v1/authorize", form=credentials)[0] == 401
print("Direct service JWT protection, token relay, and legacy-header spoofing checks passed.")
