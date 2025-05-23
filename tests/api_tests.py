#!/usr/bin/env python3
import requests, sys

BASE = "http://localhost:5000"
sess = requests.Session()

def assert_ok(r, code=200):
    if r.status_code != code:
        print(f"[FAIL] {r.request.method} {r.url} → {r.status_code}")
        sys.exit(1)

def test_health():
    r = sess.get(f"{BASE}/health")
    assert_ok(r)
    assert r.json().get("status") == "ok"

def test_public_timeline():
    r = sess.get(f"{BASE}/public")
    assert_ok(r)
    assert "<title>Timeline</title>" in r.text

def test_register_and_login_and_latest():
    user, email, pw = "ci_user", "ci@ci.test", "Password123"
    # register
    r = sess.post(f"{BASE}/register", data={
        "username": user,
        "email": email,
        "password": pw,
        "password2": pw
    }, allow_redirects=False)
    assert r.status_code == 302

    # login
    r = sess.post(f"{BASE}/login",
                  data={"username": user, "password": pw},
                  allow_redirects=False)
    assert r.status_code == 302

    # latest (should be an integer; zero if no simulator commands yet)
    r = sess.get(f"{BASE}/latest")
    assert_ok(r)
    assert isinstance(r.json().get("latest_id"), int)

    # add a message
    text = "hello-CI"
    r = sess.post(f"{BASE}/add_message",
                  data={"text": text},
                  allow_redirects=False)
    assert r.status_code == 302

    # ensure it shows up on the timeline
    r = sess.get(f"{BASE}/")
    assert_ok(r)
    assert text in r.text

def test_follow_unfollow():
    # create + login a second user
    other, pw = "ci_other", "pass"
    s2 = requests.Session()
    r = s2.post(f"{BASE}/register", data={
        "username": other, "email": "o@ci", "password": pw, "password2": pw
    }, allow_redirects=False)
    assert r.status_code == 302
    r = s2.post(f"{BASE}/login", data={"username": other, "password": pw},
                allow_redirects=False)
    assert r.status_code == 302

    # follow ci_user
    r = s2.get(f"{BASE}/ci_user/follow", allow_redirects=False)
    assert r.status_code == 302

    # verify "Unfollow" link appears
    r = s2.get(f"{BASE}/ci_user")
    assert_ok(r)
    assert "Unfollow" in r.text

    # unfollow
    r = s2.get(f"{BASE}/ci_user/unfollow", allow_redirects=False)
    assert r.status_code == 302

    # verify "Follow" link appears
    r = s2.get(f"{BASE}/ci_user")
    assert_ok(r)
    assert "Follow" in r.text

if __name__ == "__main__":
    test_health()
    test_public_timeline()
    test_register_and_login_and_latest()
    test_follow_unfollow()
    print("✅  ALL TESTS PASSED")
