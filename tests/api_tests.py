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
    assert '<title>' in r.text or '<h1>' in r.text

if __name__ == '__main__':
    test_health()
    test_public_timeline()
    print("✔ All API tests passed!")