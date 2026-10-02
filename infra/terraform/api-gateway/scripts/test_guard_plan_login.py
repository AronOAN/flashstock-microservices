import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('guard_plan', Path(__file__).with_name('guard-plan.py'))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class LoginPlanGuardTest(unittest.TestCase):
    def test_cognito_adds_only_password_flow(self):
        before = {'id': 'existing-client', 'callback_urls': ['https://example.test/auth/callback'],
                  'explicit_auth_flows': ['ALLOW_USER_SRP_AUTH', 'ALLOW_REFRESH_TOKEN_AUTH']}
        after = {**before, 'explicit_auth_flows': before['explicit_auth_flows'] + ['ALLOW_USER_PASSWORD_AUTH']}
        item = {'address': 'aws_cognito_user_pool_client.frontend', 'change': {'before': before, 'after': after}}
        self.assertTrue(guard.allowed_login_update(item))
        self.assertFalse(guard.allowed_login_update({**item, 'change': {'before': before, 'after': {**after, 'callback_urls': []}}}))

    def test_scoped_access_token_route(self):
        before = {'route_key': 'GET /api/auth/me', 'authorization_type': 'JWT',
                  'authorization_scopes': ['openid'], 'authorizer_id': 'existing-authorizer'}
        after = {**before, 'authorization_scopes': guard.ADMIN_SCOPE}
        item = {'address': 'aws_apigatewayv2_route.auth["GET /api/auth/me"]',
                'change': {'before': before, 'after': after}}
        self.assertTrue(guard.allowed_login_update(item))
        self.assertFalse(guard.allowed_login_update({**item, 'change': {'before': before, 'after': {**after, 'authorizer_id': 'other'}}}))

    def test_inventory_get_changes_from_public_to_jwt(self):
        before = {'route_key': 'GET /api/inventory', 'authorization_type': 'NONE',
                  'authorization_scopes': None, 'authorizer_id': None}
        after = {**before, 'authorization_type': 'JWT', 'authorization_scopes': guard.ADMIN_SCOPE,
                 'authorizer_id': 'existing-authorizer'}
        item = {'address': 'aws_apigatewayv2_route.inventory["GET /api/inventory"]',
                'change': {'before': before, 'after': after}}
        self.assertTrue(guard.allowed_login_update(item))
        self.assertFalse(guard.allowed_login_update({**item, 'change': {'before': before, 'after': {**after, 'authorization_scopes': ['openid']}}}))


if __name__ == '__main__':
    unittest.main()
