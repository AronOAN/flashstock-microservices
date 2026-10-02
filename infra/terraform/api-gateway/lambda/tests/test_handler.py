import os
import sys
import json
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from handler import lambda_handler

os.environ["COGNITO_ISSUER_URL"] = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_jkrNUk7yQ"
os.environ["COGNITO_APP_CLIENT_ID"] = "1v1gmjscc2qtpuerhs63v6taob"

def event(groups, **overrides):
    claims = dict(iss_=os.environ["COGNITO_ISSUER_URL"], token_use="access",
                  client_id=os.environ["COGNITO_APP_CLIENT_ID"],sub="stable-sub",scope="openid email",
                  **{"cognito:groups":groups})
    claims["iss"] = claims.pop("iss_")
    claims.update(overrides)
    return {"routeKey":"GET /api/auth/permissions", "headers":{"authorization":"ignored"},
            "requestContext":{"authorizer":{"jwt":{"claims":claims,"scopes":["openid"]}}}}

class PermissionsTest(unittest.TestCase):
    def result(self, e):
        r=lambda_handler(e, None)
        return r["statusCode"],json.loads(r["body"])["data"]
    def test_user(self):
        status,data=self.result(event(["USER"]))
        self.assertEqual(status,200)
        self.assertEqual(data["authorities"],["ROLE_USER"])
        self.assertNotIn("inventory:write",data["permissions"])
    def test_admin_inherits_user(self):
        status,data=self.result(event(["ADMIN"]))
        self.assertEqual(status,200)
        self.assertEqual(set(data["authorities"]), {"ROLE_ADMIN", "ROLE_USER"})
    def test_admin(self):
        status,data=self.result(event('["USER", "ADMIN"]'))
        self.assertEqual(status,200)
        self.assertIn("ROLE_ADMIN",data["authorities"])
        self.assertIn("inventory:write",data["permissions"])
    def test_legacy_name_cannot_escalate(self):
        status,data=self.result(event(["solicitudes-dev", "ROLE_ADMIN", "admin"]))
        self.assertEqual(status,200)
        self.assertEqual(data["authorities"],[])
    def test_raw_jwt_unverified_denied(self):
        e={"routeKey":"GET /api/auth/permissions","headers":{"authorization":"Bearer x.y.z"}}
        self.assertEqual(self.result(e)[0],401)
    def test_id_token_denied(self):
        self.assertEqual(self.result(event(["ADMIN"],token_use="id"))[0],401)
    def test_client_mismatch_denied(self):
        self.assertEqual(self.result(event(["ADMIN"],client_id="attacker"))[0],401)
    def test_wrong_scope(self):
        self.assertEqual(self.result(event(["ADMIN"],scope="email"))[0],403)
    def test_no_route_bypass(self):
        e=event(["ADMIN"]); e["routeKey"]="GET /api/admin/metrics"
        self.assertEqual(self.result(e)[0],403)

if __name__=="__main__": unittest.main()
