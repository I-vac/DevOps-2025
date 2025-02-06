# -*- coding: utf-8 -*-
"""
    MiniTwit Tests (Updated for Python 3)
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Tests the MiniTwit application.

    :copyright: (c) 2010 by Armin Ronacher.
    :license: BSD, see LICENSE for more details.
"""
import os
import unittest
import tempfile
import minitwit


class MiniTwitTestCase(unittest.TestCase):

    def setUp(self):
        """Before each test, set up a blank database"""
        self.db = tempfile.NamedTemporaryFile(delete=False)  # Keep file so Flask can access it
        self.app = minitwit.app.test_client()
        minitwit.DATABASE = self.db.name
        minitwit.init_db()

    def tearDown(self):
        """Clean up temporary files"""
        self.db.close()
        os.unlink(self.db.name)  # Delete the temporary file

    def get_text(self, response):
        """Helper function to decode response data"""
        return response.data.decode('utf-8')

    # Helper functions

    def register(self, username, password, password2=None, email=None):
        """Helper function to register a user"""
        if password2 is None:
            password2 = password
        if email is None:
            email = f"{username}@example.com"
        return self.app.post('/register', data={
            'username': username,
            'password': password,
            'password2': password2,
            'email': email,
        }, follow_redirects=True)

    def login(self, username, password):
        """Helper function to login"""
        return self.app.post('/login', data={
            'username': username,
            'password': password
        }, follow_redirects=True)

    def register_and_login(self, username, password):
        """Registers and logs in in one go"""
        self.register(username, password)
        return self.login(username, password)

    def logout(self):
        """Helper function to logout"""
        return self.app.get('/logout', follow_redirects=True)

    def add_message(self, text):
        """Records a message"""
        rv = self.app.post('/add_message', data={'text': text}, follow_redirects=True)
        if text:
            assert 'Your message was recorded' in self.get_text(rv)
        return rv

    # Testing functions

    def test_register(self):
        """Make sure registering works"""
        rv = self.register('user1', 'default')
        assert 'You were successfully registered and can login now' in self.get_text(rv)
        rv = self.register('user1', 'default')
        assert 'The username is already taken' in self.get_text(rv)
        rv = self.register('', 'default')
        assert 'You have to enter a username' in self.get_text(rv)
        rv = self.register('meh', '')
        assert 'You have to enter a password' in self.get_text(rv)
        rv = self.register('meh', 'x', 'y')
        assert 'The two passwords do not match' in self.get_text(rv)
        rv = self.register('meh', 'foo', email='broken')
        assert 'You have to enter a valid email address' in self.get_text(rv)

    def test_login_logout(self):
        """Make sure logging in and logging out works"""
        rv = self.register_and_login('user1', 'default')
        assert 'You were logged in' in self.get_text(rv)
        rv = self.logout()
        assert 'You were logged out' in self.get_text(rv)
        rv = self.login('user1', 'wrongpassword')
        assert 'Invalid password' in self.get_text(rv)
        rv = self.login('user2', 'wrongpassword')
        assert 'Invalid username' in self.get_text(rv)

    def test_message_recording(self):
        """Check if adding messages works"""
        self.register_and_login('foo', 'default')
        self.add_message('test message 1')
        self.add_message('<test message 2>')
        rv = self.app.get('/')
        assert 'test message 1' in self.get_text(rv)
        assert '&lt;test message 2&gt;' in self.get_text(rv)

    def test_timelines(self):
        """Make sure that timelines work"""
        self.register_and_login('foo', 'default')
        self.add_message('the message by foo')
        self.logout()
        self.register_and_login('bar', 'default')
        self.add_message('the message by bar')
        rv = self.app.get('/public')
        assert 'the message by foo' in self.get_text(rv)
        assert 'the message by bar' in self.get_text(rv)

        # bar's timeline should just show bar's message
        rv = self.app.get('/')
        assert 'the message by foo' not in self.get_text(rv)
        assert 'the message by bar' in self.get_text(rv)

        # now let's follow foo
        rv = self.app.get('/foo/follow', follow_redirects=True)
        assert 'You are now following &#34;foo&#34;' in self.get_text(rv)

        # we should now see foo's message
        rv = self.app.get('/')
        assert 'the message by foo' in self.get_text(rv)
        assert 'the message by bar' in self.get_text(rv)

        # but on the user's page we only want the user's message
        rv = self.app.get('/bar')
        assert 'the message by foo' not in self.get_text(rv)
        assert 'the message by bar' in self.get_text(rv)
        rv = self.app.get('/foo')
        assert 'the message by foo' in self.get_text(rv)
        assert 'the message by bar' not in self.get_text(rv)

        # now unfollow and check if that worked
        rv = self.app.get('/foo/unfollow', follow_redirects=True)
        assert 'You are no longer following &#34;foo&#34;' in self.get_text(rv)
        rv = self.app.get('/')
        assert 'the message by foo' not in self.get_text(rv)
        assert 'the message by bar' in self.get_text(rv)


if __name__ == '__main__':
    unittest.main()
