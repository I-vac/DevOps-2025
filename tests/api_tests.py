#!/usr/bin/env python3
import requests

BASE = 'http://localhost:5000'

def test_health():
    r = requests.get(f'{BASE}/health')
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}

def test_public_timeline():
    r = requests.get(f'{BASE}/public')
    assert r.status_code == 200
    # we should have some HTML <title> or <h1> in the timeline page
    assert '<title>' in r.text or '<h1>' in r.text

def test_register_login_and_post():
    s = requests.Session()
    username = 'ci_user'
    password = 'supersecret'
    email = 'ci@example.com'

    # 1) register
    r = s.post(f'{BASE}/register', data={
        'username': username,
        'email': email,
        'password': password,
        'password2': password,
    }, allow_redirects=False)
    assert r.status_code == 302
    assert r.headers['Location'] == '/login'

    # 2) login
    r = s.post(f'{BASE}/login', data={
        'username': username,
        'password': password,
    }, allow_redirects=False)
    assert r.status_code == 302
    assert r.headers['Location'] == '/'

    # 3) check /latest (should be an integer)
    r = s.get(f'{BASE}/latest')
    assert r.status_code == 200
    data = r.json()
    assert 'latest_id' in data and isinstance(data['latest_id'], int)
    before = data['latest_id']

    # 4) post a new message
    message = 'Hello from CI'
    r = s.post(f'{BASE}/add_message', data={'text': message}, allow_redirects=False)
    assert r.status_code == 302
    assert r.headers['Location'] == '/'

    # 5) see it on the public timeline
    r = s.get(f'{BASE}/public')
    assert message in r.text

    # 6) /latest should have increased
    r = s.get(f'{BASE}/latest')
    after = r.json()['latest_id']
    assert after >= before + 1

if __name__ == '__main__':
    test_health()
    test_public_timeline()
    test_register_login_and_post()
    print("✔ All API tests passed!")
