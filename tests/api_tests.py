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

    assert r.status_code == 302, f"❌ Registration failed (expected 302): {r.status_code}, body: {r.text}"
    loc = r.headers.get('Location', '')
    assert loc.endswith('/login'), f"❌ Unexpected redirect after register: {loc}"

    # 2) login → should redirect to /
    r = s.post(f'{BASE}/login', data={
        'username': username,
        'password': password,
    }, allow_redirects=False)

    assert r.status_code == 302, f"❌ Login failed (expected 302): {r.status_code}, body: {r.text}"
    loc = r.headers.get('Location', '')
    assert loc.rstrip('/').endswith(''), f"❌ Unexpected Location after login: {loc}"

    # 3) Confirm user is logged in by visiting /
    dashboard = s.get(f'{BASE}/')
    assert dashboard.status_code == 200, f"❌ Dashboard failed: {dashboard.status_code}"
    assert 'Your timeline' in dashboard.text or 'logout' in dashboard.text.lower(), \
        f"❌ User might not be logged in. Page content:\n{dashboard.text[:500]}"

    # 4) check /latest
    r = s.get(f'{BASE}/latest')
    assert r.status_code == 200, "❌ Failed to fetch /latest"
    data = r.json()
    assert 'latest_id' in data and isinstance(data['latest_id'], int), f"❌ Invalid /latest response: {data}"
    before = data['latest_id']

    # 5) post a new message
    message = f'Hello from CI - {uuid.uuid4().hex[:6]}'
    r = s.post(f'{BASE}/add_message', data={'text': message}, allow_redirects=False)
    assert r.status_code == 302, f"❌ Message post failed: {r.status_code} - {r.text}"
    loc = r.headers.get('Location', '')
    assert loc.endswith('/'), f"❌ Unexpected Location after message post: {loc}"

    # 6) confirm it's on /public
    r = s.get(f'{BASE}/public')
    assert message in r.text, f"❌ Message not found on /public: {message}"

    # 7) latest_id should bump
    r = s.get(f'{BASE}/latest')
    after = r.json().get('latest_id')
    assert after >= before + 1, f"❌ latest_id not bumped: before={before}, after={after}"

if __name__ == '__main__':
    test_health()
    test_public_timeline()
    test_register_login_and_post()
    print("✔ All API tests passed!")
