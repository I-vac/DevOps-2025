#!/usr/bin/env python3
import requests
import uuid

BASE = 'http://localhost:5000'

def test_health():
    r = requests.get(f'{BASE}/health')
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}

def test_public_timeline():
    r = requests.get(f'{BASE}/public')
    assert r.status_code == 200
    assert '<title>' in r.text or '<h1>' in r.text

def test_register_login_and_post():
    s = requests.Session()
    username = f'ci_user_{uuid.uuid4().hex[:8]}'
    password = 'supersecret'
    email = f'{username}@example.com'

    # 1) register → should redirect to /login
    r = s.post(f'{BASE}/register', data={
        'username': username,
        'email': email,
        'password': password,
        'password2': password,
    }, allow_redirects=False)

    if r.status_code != 302:
        print("❌ Registration failed.")
        print("Status code:", r.status_code)
        print("Response headers:", r.headers)
        print("Response body:", r.text)
    assert r.status_code == 302, f"Expected 302 but got {r.status_code}, body: {r.text}"

    loc = r.headers.get('Location', '')
    assert loc.endswith('/login'), f"Unexpected Location: {loc}"

    # 3) check /latest
    r = s.get(f'{BASE}/latest')
    assert r.status_code == 200
    data = r.json()
    assert 'latest_id' in data and isinstance(data['latest_id'], int)
    before = data['latest_id']

    # 4) post a new message
    message = 'Hello from CI'
    r = s.post(f'{BASE}/add_message', data={'text': message}, allow_redirects=False)
    assert r.status_code == 302
    loc = r.headers.get('Location', '')
    assert loc.endswith('/'), f"unexpected Location: {loc}"

    # 5) see it on /public
    r = s.get(f'{BASE}/public')
    assert message in r.text

    # 6) /latest bumped
    r = s.get(f'{BASE}/latest')
    after = r.json()['latest_id']
    assert after >= before + 1

if __name__ == '__main__':
    test_health()
    test_public_timeline()
    test_register_login_and_post()
    print("✔ All API tests passed!")