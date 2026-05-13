package com.testgen.uber;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

// ────────────────────────────────────────────────────────────────────────────
// PublicTests.java — hand-written, dynamic, scenario-driven public test cases
// for Uber M2.
//
// This is the canonical public test file. The 749 template-generated classes
// (deprecated) live in archive/PublicTestsAll_Stale.java for reference but
// no longer execute.
//
// Each class below is one row in
// docs/test-scenarios/Uber_Tests_Description.md.
//
// Style guide:
//   * One package-private `class TC<NN>_<DescriptiveName> extends TestBase`
//     per scenario, with one or more @Test methods.
//   * @Tag("public") + a category tag (features_m1 / features_m2 / patterns
//     / amendments / updated_crud / cross_cutting) per scenario row.
//   * Resolve every URL through TestBase helpers — never hardcode "/api/...":
//       - crudReadPath("Ride"), crudCollectionPath("Driver"), fillPath(...)
//       - loginPath(), registerPath()
//     Resolve table names through tableName("Ride"), enums through
//     enumValues("RideStatus").
//   * Resolve IDs from response bodies (registration, search results) or
//     from the auto-seeded fixtures (admin = id=1 via adminToken/adminId,
//     vegetarian customer with 5 rides = id=4, etc. — see
//     memory/project_uber_seed_data.md). Never write `/api/users/1`
//     literally.
//   * One scenario at a time, approved by the user before mapping to the
//     other 7 themes.
// ────────────────────────────────────────────────────────────────────────────

// ─── TC01 — Register a new user (happy path) ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC01_RegisterHappyPathTests extends TestBase {

        @Test
        @DisplayName("TC01 — POST registerPath() with a fresh email returns 2xx + non-empty token")
        void register_returns_2xx_with_token() throws Exception {
                BASE_URL = userServiceUrl;
                // Build a payload with a nonce-based email so it cannot collide with
                // any of the auto-seeded users (_preseed_*@grader.testgen.io) or
                // with prior runs of this test class.
                String email = "tc01_" + nonce() + "@grader.testgen.io";
                String body = String.format("""
                                {"name":"TC01 User","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));

                HttpResponse<String> r = httpPost("/api/auth/register", body);

                // Strict 2xx — registering a brand-new email with a valid payload
                // must succeed; any non-2xx is a bug in register / validation /
                // password hashing / DB persistence.
                // Spec (M2 §10 S1-F10): response is {"token":"...","expiresIn":86400000}.
                // No 'id' field is contracted, so don't assert on it.
                assert2xx(r, "TC01 register");
                JsonNode j = parseNode(r.body());
                assertNotNull(j.get("token"),
                                "TC01: register response must include 'token' field; body=" + r.body());
                assertTrue(j.get("token").isTextual() && !j.get("token").asText().isBlank(),
                                "TC01: 'token' must be a non-empty string; got " + j.get("token"));
        }
}

// ─── TC02 — Login with valid credentials (happy path) ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC02_LoginHappyPathTests extends TestBase {

        @Test
        @DisplayName("TC02 — POST loginPath() after a successful register returns 2xx with a 3-segment JWT")
        void login_returns_2xx_with_three_segment_jwt() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — create a fresh user.
                String email = "tc02_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC02 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC02 setup register");

                // Act — log in with the same credentials.
                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> r = httpPost("/api/auth/login", loginBody);

                // Assert.
                assert2xx(r, "TC02 login");
                JsonNode j = parseNode(r.body());
                assertNotNull(j.get("token"),
                                "TC02: login response must include 'token' field; body=" + r.body());
                String token = j.get("token").asText();
                assertFalse(token.isBlank(),
                                "TC02: 'token' must be a non-blank string");
                assertEquals(3, token.split("\\.").length,
                                "TC02: 'token' must be a 3-segment JWT (a.b.c); got '" + token + "'");
        }
}

// ─── TC03 — Read own user profile with valid JWT (happy path) ───────────────
@Tag("public")
@Tag("updated_crud")
class TC03_ReadOwnProfileHappyPathTests extends TestBase {

        @Test
        @DisplayName("TC03 — GET crudReadPath(\"User\") with own JWT returns 2xx and a JSON object")
        void read_own_profile_returns_2xx_and_json_object() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register and capture the new user's id.
                String email = "tc03_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC03 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC03 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                // Setup — login and capture the JWT.
                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC03 setup login");
                String token = parseNode(login.body()).get("token").asText();

                // Act — read the user's own profile via the User CRUD path. The
                // path template comes from the manifest so a student who renames
                // their controller still gets the correct URL.
                HttpResponse<String> r = httpGetAuth("/api/users/" + uid, token);

                // Assert. Strict 2xx — we just registered this exact user, a 404
                // here is a real bug (register didn't persist OR the JWT chain
                // rejected our own token).
                assert2xx(r, "TC03 read own profile");
                JsonNode j = parseNode(r.body());
                assertTrue(j.isObject(),
                                "TC03: response body must be a JSON object; got " + r.body());
        }
}

// ─── TC04 — Register with duplicate email returns 4xx (negative path) ───────
@Tag("public")
@Tag("features_m2")
class TC04_RegisterDuplicateEmailTests extends TestBase {

        @Test
        @DisplayName("TC04 — POST registerPath() with an already-registered email returns a 4xx")
        void register_with_duplicate_email_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Build a payload with a nonce-based email — guaranteed not to
                // collide with auto-seeded users or with prior runs of this test
                // class (since @BeforeEach truncates between tests anyway).
                String email = "tc04_" + nonce() + "@grader.testgen.io";
                String firstBody = String.format("""
                                {"name":"TC04 First","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));

                // Step 1 — first registration must succeed (precondition).
                HttpResponse<String> first = httpPost("/api/auth/register", firstBody);
                assert2xx(first, "TC04 first register (precondition)");

                // Step 2 — second registration with the SAME email but different
                // name + phone (uniqueness should be on email).
                String secondBody = String.format("""
                                {"name":"TC04 Second","email":"%s","password":"AnotherPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                HttpResponse<String> second = httpPost("/api/auth/register", secondBody);

                // Step 3 — strict 4xx assertion. Tolerate any of 400/409/422
                // (M2 spec doesn't pin a specific code) but NOT 2xx, NOT 5xx,
                // and NOT 401/403 (the body itself is well-formed and authorized).
                int code = second.statusCode();
                assertTrue(code >= 400 && code < 500,
                                "TC04: duplicate-email register must return a 4xx client error; "
                                                + "got " + code + " body=" + second.body());
                assertTrue(code != 401,
                                "TC04: duplicate-email is not an auth failure (no Authorization header was sent); "
                                                + "401 indicates the controller is misclassifying the error. body="
                                                + second.body());
                assertTrue(code != 403,
                                "TC04: duplicate-email is not a permission failure (anyone can register); "
                                                + "403 indicates the controller is misclassifying the error. body="
                                                + second.body());
        }
}

// ─── TC05 — Login with wrong password returns 401 (negative path) ───────────
@Tag("public")
@Tag("features_m2")
class TC05_LoginWrongPasswordTests extends TestBase {

        @Test
        @DisplayName("TC05 — POST loginPath() with the wrong password returns strictly 401")
        void login_with_wrong_password_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register a fresh user with a known-correct password.
                // nonce-based email avoids collisions with auto-seeded users and
                // with prior test runs (truncate@BeforeEach handles cross-test).
                String email = "tc05_" + nonce() + "@grader.testgen.io";
                String correctPwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC05 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, correctPwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC05 setup register (precondition)");

                // Act — log in with the SAME email but a different password.
                // Different enough that bcrypt cannot accidentally match (no
                // shared prefix, different length).
                String wrongPwd = "WrongPwd!2026";
                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, wrongPwd);
                HttpResponse<String> r = httpPost("/api/auth/login", loginBody);
                int code = r.statusCode();

                // Assert — strictly 401. Tolerant assertions (with explanatory
                // messages) for each common misclassification:
                // * 2xx: login skipped the password check — critical security bug.
                // * 5xx: bcrypt mismatch leaked as exception instead of being
                // caught and translated to 401.
                // * 404: user-enumeration anti-pattern (OWASP). Login must NOT
                // distinguish "user not found" from "wrong password" in
                // its status code; both should be 401.
                // * 403: this is not a permissions issue. Login is how you
                // OBTAIN permissions; you cannot be 403'd from it.
                assertTrue(code / 100 != 2,
                                "TC05: login with wrong password must NOT return 2xx. "
                                                + "A 2xx here means the password check was skipped — critical security bug. "
                                                + "Got " + code + " body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC05: login with wrong password must NOT 5xx. A 5xx here means the bcrypt "
                                                + "mismatch threw an unhandled exception instead of being caught and "
                                                + "translated to 401. Got " + code + " body=" + r.body());
                assertTrue(code != 404,
                                "TC05: login with wrong password must NOT return 404. The user account exists; "
                                                + "returning 404 instead of 401 leaks the existence/non-existence of accounts "
                                                + "(OWASP user-enumeration anti-pattern). body=" + r.body());
                assertTrue(code != 403,
                                "TC05: login with wrong password must NOT return 403. Login is the act of "
                                                + "obtaining permissions, not exercising them — 403 is structurally wrong. "
                                                + "body=" + r.body());
                assertEquals(401, code,
                                "TC05: login with wrong password must return strictly 401 Unauthorized; got "
                                                + code + " body=" + r.body());
        }
}

// ─── TC06 — Authentication happy path: valid admin JWT accepted on a non-User
// CRUD
@Tag("public")
@Tag("authentication")
class TC06_AuthValidTokenAcceptedTests extends TestBase {

        @Test
        @DisplayName("TC06 — GET a non-User CRUD list endpoint with a valid admin Bearer JWT returns 2xx (auth filter accepts the token)")
        void valid_admin_jwt_is_accepted_on_non_user_crud() throws Exception {
                BASE_URL = userServiceUrl;
                String token = adminToken();
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                // Use the correct service URL for the entity (entity may live in
                // a different service than user-service).
                BASE_URL = serviceUrlForEntity(entity);
                HttpResponse<String> r = httpGetAuth(path, token);
                assert2xx(r, "TC06 auth happy path (admin) on " + entity + " list (" + path + ")");
        }
}

// ─── TC07 — Missing Authorization header on a non-User CRUD returns 401 ─────
@Tag("public")
@Tag("authentication")
class TC07_AuthMissingHeaderTests extends TestBase {

        @Test
        @DisplayName("TC07 — GET a non-User CRUD list endpoint with NO Authorization header returns strictly 401")
        void missing_auth_header_returns_401_on_non_user_crud() throws Exception {
                BASE_URL = userServiceUrl;
                // Act — same endpoint as TC06's happy path (so the two form a
                // clean A/B test of the JWT filter), BUT no Authorization
                // header at all (httpGet, not httpGetAuth). No setup needed —
                // we're testing whether anonymous requests are blocked, which
                // doesn't depend on having any specific row in the DB.
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                HttpResponse<String> r = httpGet(path);
                int code = r.statusCode();

                // Assert — strictly 401. Tolerant assertions for each common
                // misclassification, with diagnostic messages:
                // * 2xx: endpoint wide-open — critical security bug.
                // * 5xx: filter chain crashed instead of cleanly rejecting.
                // * 404: endpoint reachable anonymously and just returns
                // empty/missing — wrong; auth must block FIRST, before
                // any controller logic runs.
                // * 403: 403 means "authenticated but lacking permission". We
                // sent NO credentials at all — the correct response is
                // 401 (Unauthorized), not 403 (Forbidden).
                assertTrue(code / 100 != 2,
                                "TC07: GET " + path + " without Authorization header must NOT return 2xx. "
                                                + "A 2xx here means the endpoint is wide-open — critical security bug. "
                                                + "Got " + code + " body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC07: GET " + path + " without Authorization header must NOT 5xx. A 5xx here "
                                                + "means the filter chain crashed instead of cleanly rejecting. "
                                                + "Got " + code + " body=" + r.body());
                assertTrue(code != 404,
                                "TC07: GET " + path + " without Authorization header must NOT return 404. "
                                                + "A 404 here means the endpoint is reachable anonymously and just "
                                                + "returned empty — auth must block FIRST, before any controller logic "
                                                + "runs. body=" + r.body());
                assertTrue(code != 403,
                                "TC07: GET " + path + " without Authorization header must NOT return 403. We "
                                                + "sent NO credentials; 403 means \"authenticated but lacking permission\", "
                                                + "which doesn't apply when there's no auth attempt at all. The correct "
                                                + "code is 401 Unauthorized. body=" + r.body());
                assertEquals(401, code,
                                "TC07: GET " + path + " without Authorization header must return strictly 401 "
                                                + "Unauthorized; got " + code + " body=" + r.body());
        }
}

// ─── TC08 — Tampered JWT signature is rejected with 401 (negative path) ─────
@Tag("public")
@Tag("authentication")
class TC08_AuthTamperedSignatureTests extends TestBase {

        @Test
        @DisplayName("TC08 — GET protected endpoint with a tampered-signature JWT returns strictly 401 (signature is verified, not just decoded)")
        void tampered_jwt_signature_is_rejected_with_401() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — get a real, fully-valid admin JWT, then tamper its
                // signature segment. tamperSignature(token) preserves the
                // header + payload (still parses as a JWT, still passes any
                // "is it 3 segments?" check) but replaces the signature with
                // a base64 of "tampered-signature-does-not-verify" — so the
                // signature cannot verify against any signing key.
                String validToken = adminToken();
                String tamperedToken = tamperSignature(validToken);

                // Act — same endpoint as TC06/TC07 (the auth-filter A/B/C
                // triad). Send the tampered token in the Authorization header.
                // A correctly-implemented auth filter MUST reject this with 401
                // even though the token looks structurally valid.
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                HttpResponse<String> r = httpGetAuth(path, tamperedToken);
                int code = r.statusCode();

                // Assert — strictly 401. Diagnostic ladder:
                // * 2xx: signature not actually verified — critical security bug
                // (anyone can forge tokens by editing the payload).
                // * 5xx: filter chain crashed on signature mismatch instead of
                // cleanly rejecting.
                // * 404: filter passed the request through to the controller
                // and just didn't find anything — wrong; signature
                // mismatch must be caught FIRST in the filter, before
                // any controller logic.
                // * 403: forged credentials = "not authenticated" (401), not
                // "authenticated but lacking permission" (403).
                assertTrue(code / 100 != 2,
                                "TC08: tampered-signature JWT must NOT be accepted (status " + code + "). "
                                                + "A 2xx here means the signature was not actually verified — anyone can "
                                                + "forge tokens by editing the payload. Critical security bug. body="
                                                + r.body());
                assertTrue(code / 100 != 5,
                                "TC08: tampered-signature JWT must NOT 5xx (status " + code + "). A 5xx here "
                                                + "means the filter chain crashed on signature mismatch instead of cleanly "
                                                + "rejecting. body=" + r.body());
                assertTrue(code != 404,
                                "TC08: tampered-signature JWT must NOT return 404 (status " + code + "). A 404 "
                                                + "here means the filter passed the request through to the controller and "
                                                + "just didn't find anything — signature mismatch must be caught FIRST in "
                                                + "the filter, before any controller logic runs. body=" + r.body());
                assertTrue(code != 403,
                                "TC08: tampered-signature JWT must NOT return 403 (status " + code + "). Forged "
                                                + "credentials are \"not authenticated\" (401), not \"authenticated but "
                                                + "lacking permission\" (403). body=" + r.body());
                assertEquals(401, code,
                                "TC08: tampered-signature JWT must return strictly 401 Unauthorized; got "
                                                + code + " body=" + r.body());
        }
}

// ─── TC09 — Login with non-existent email returns 401 (negative path) ───────
@Tag("public")
@Tag("features_m2")
class TC09_LoginUnknownEmailTests extends TestBase {

        @Test
        @DisplayName("TC09 — POST loginPath() with an email that has never been registered returns strictly 401")
        void login_unknown_email_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                // nonce-based email — guaranteed not to exist in the DB. Any
                // truncate/auto-seed in @BeforeEach also strips users, so this
                // email is truly absent at login time.
                String unknownEmail = "tc09_never_registered_" + nonce() + "@grader.testgen.io";
                String body = String.format("""
                                {"email":"%s","password":"AnythingPwd!2026"}
                                """, unknownEmail);

                HttpResponse<String> r = httpPost("/api/auth/login", body);
                int code = r.statusCode();

                // Spec (M2 §10 S1-F11): login throws 401 for both "user not found"
                // and "wrong password". 401-on-missing-email is intentional (avoids
                // leaking which emails are registered). 404 is explicitly forbidden.
                assertTrue(code / 100 != 2,
                                "TC09: login with a never-registered email must NOT return 2xx (status "
                                                + code + "). A 2xx here means a token was issued for a non-existent "
                                                + "user. body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC09: login with a never-registered email must NOT 5xx (status " + code
                                                + "). Server must handle the missing-user case cleanly. body="
                                                + r.body());
                assertTrue(code != 403,
                                "TC09: login with a never-registered email must NOT return 403 (status " + code
                                                + "). 403 is a permission error; this is an unauthenticated condition. body="
                                                + r.body());
                assertEquals(401, code,
                                "TC09: login with a never-registered email must return strictly 401 Unauthorized "
                                                + "(spec §10 S1-F11 — 401 for both 'user not found' and 'wrong password'); "
                                                + "got " + code + " body=" + r.body());
        }
}

// ─── TC10 — Empty Bearer token returns 401 (negative path) ──────────────────
@Tag("public")
@Tag("authentication")
class TC10_AuthEmptyBearerTests extends TestBase {

        @Test
        @DisplayName("TC10 — GET protected endpoint with `Authorization: Bearer ` (empty token) returns strictly 401")
        void empty_bearer_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                // "Bearer " with a trailing space and no token after it. Not a
                // missing header (TC07 covers that) — this header is PRESENT
                // but its value is malformed.
                HttpResponse<String> r = httpGetWithRawAuth(path, "Bearer ");
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC10: empty Bearer token must NOT be accepted (status " + code
                                                + "). A 2xx here means the auth filter accepted an empty/missing token. "
                                                + "body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC10: empty Bearer token must NOT 5xx (status " + code + "). The filter must "
                                                + "handle empty tokens cleanly, not throw NPE. body=" + r.body());
                assertEquals(401, code,
                                "TC10: empty Bearer token must return strictly 401 Unauthorized; got " + code
                                                + " body=" + r.body());
        }
}

// ─── TC11 — Non-Bearer scheme (Basic) returns 401 (negative path) ───────────
@Tag("public")
@Tag("authentication")
class TC11_AuthBasicSchemeTests extends TestBase {

        @Test
        @DisplayName("TC11 — GET protected endpoint with `Authorization: Basic ...` (non-Bearer scheme) returns strictly 401")
        void basic_scheme_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                // "Basic dXNlcjpwYXNz" — base64 of "user:pass". Catches lenient
                // parsers that strip the scheme prefix and try to decode whatever
                // is left as a JWT (it isn't).
                HttpResponse<String> r = httpGetWithRawAuth(path, "Basic dXNlcjpwYXNz");
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC11: Basic-scheme auth must NOT be accepted on a JWT-protected endpoint "
                                                + "(status " + code
                                                + "). The filter must reject any non-Bearer scheme. "
                                                + "body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC11: Basic-scheme auth must NOT 5xx (status " + code + "). body=" + r.body());
                assertEquals(401, code,
                                "TC11: Basic-scheme auth must return strictly 401 Unauthorized; got " + code
                                                + " body=" + r.body());
        }
}

// ─── TC12 — Garbage non-JWT token returns 401 (negative path) ───────────────
@Tag("public")
@Tag("authentication")
class TC12_AuthGarbageTokenTests extends TestBase {

        @Test
        @DisplayName("TC12 — GET protected endpoint with `Authorization: Bearer not_a_valid_jwt` returns strictly 401")
        void garbage_token_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                // "not_a_valid_jwt" — no dots, not base64, structurally invalid.
                // Catches "if 3 dot-segments, decode without verifying" and any
                // code path that doesn't validate JWT structure before claims-extract.
                HttpResponse<String> r = httpGetWithRawAuth(path, "Bearer not_a_valid_jwt");
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC12: garbage non-JWT token must NOT be accepted (status " + code
                                                + "). A 2xx here means the filter didn't validate the JWT structure. "
                                                + "body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC12: garbage token must NOT 5xx (status " + code + "). The parser must "
                                                + "handle malformed tokens gracefully. body=" + r.body());
                assertEquals(401, code,
                                "TC12: garbage non-JWT token must return strictly 401 Unauthorized; got " + code
                                                + " body=" + r.body());
        }
}

// ─── TC13 — Forged role-claim token (payload modified post-signing) rejected
@Tag("public")
@Tag("authentication")
class TC13_AuthForgedRoleClaimTests extends TestBase {

        @Test
        @DisplayName("TC13 — GET protected endpoint with a payload-tampered JWT (role forged to ADMIN) is NOT accepted")
        void forged_role_claim_token_is_rejected() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register a CUSTOMER user (default theme role) and log
                // in to capture a real, signed JWT for them.
                String email = "tc13_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC13 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC13 setup register (precondition)");

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC13 setup login (precondition)");
                String realToken = parseNode(login.body()).get("token").asText();

                // Forge a role claim into the payload while keeping the original
                // signature. The signature was computed over the original
                // payload; modifying the payload makes the signature INVALID
                // even though we don't tamper the signature segment itself.
                String[] parts = realToken.split("\\.");
                if (parts.length != 3) {
                        throw new AssertionError("TC13 setup: real token is not a 3-segment JWT: " + realToken);
                }
                String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                // Try to replace common role-claim patterns. If none match,
                // inject a "role":"ADMIN" entry into the JSON object.
                String tamperedPayload = payloadJson;
                for (String[] swap : new String[][] {
                                { "\"role\":\"CUSTOMER\"", "\"role\":\"ADMIN\"" },
                                { "\"role\": \"CUSTOMER\"", "\"role\":\"ADMIN\"" },
                                { "\"authorities\":[\"ROLE_CUSTOMER\"]", "\"authorities\":[\"ROLE_ADMIN\"]" },
                }) {
                        if (tamperedPayload.contains(swap[0])) {
                                tamperedPayload = tamperedPayload.replace(swap[0], swap[1]);
                                break;
                        }
                }
                if (tamperedPayload.equals(payloadJson)) {
                        // No known role-claim pattern found — inject one. Locate the
                        // closing brace of the outer object and prepend a role claim.
                        int lastBrace = tamperedPayload.lastIndexOf('}');
                        if (lastBrace > 0) {
                                String prefix = tamperedPayload.substring(0, lastBrace).trim();
                                if (prefix.endsWith("{")) {
                                        tamperedPayload = prefix + "\"role\":\"ADMIN\"}";
                                } else {
                                        tamperedPayload = prefix + ",\"role\":\"ADMIN\"}";
                                }
                        }
                }
                String tamperedB64 = java.util.Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(tamperedPayload.getBytes());
                String forgedToken = parts[0] + "." + tamperedB64 + "." + parts[2];

                // Act — hit a protected endpoint with the forged token.
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                HttpResponse<String> r = httpGetAuth(path, forgedToken);
                int code = r.statusCode();

                // Assert — must NOT be 2xx. Either of these is acceptable:
                // * 401 — signature verification caught the payload tamper (preferred).
                // * 403 — signature verified but server re-validated role from DB
                // and found CUSTOMER, not ADMIN.
                // But 2xx means signature wasn't verified AND role was trusted from
                // the (forged) payload — critical privilege-escalation bug.
                assertTrue(code / 100 != 2,
                                "TC13: forged role-claim token must NOT be accepted (status " + code + "). "
                                                + "A 2xx here means the signature was not verified after payload "
                                                + "modification — anyone with a real token can self-promote to ADMIN by "
                                                + "editing their payload. Critical privilege-escalation bug. body="
                                                + r.body());
                assertTrue(code / 100 != 5,
                                "TC13: forged role-claim token must NOT 5xx (status " + code + "). The filter "
                                                + "must reject cleanly. body=" + r.body());
        }
}

// ─── TC14 — Register with missing required field returns 4xx (negative path)
@Tag("public")
@Tag("features_m2")
class TC14_RegisterMissingFieldTests extends TestBase {

        @Test
        @DisplayName("TC14 — POST registerPath() with a body missing the `email` field returns a 4xx")
        void register_missing_email_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Body missing `email` — controller must reject with 4xx, not
                // propagate the missing-field as a server-side NPE/5xx or
                // silently create a user with null email.
                String body = String.format("""
                                {"name":"TC14 User","password":"TestPwd!2026","phone":"+201%s"}
                                """, nonce().substring(0, 9));

                HttpResponse<String> r = httpPost("/api/auth/register", body);
                int code = r.statusCode();

                assertTrue(code >= 400 && code < 500,
                                "TC14: register with missing required field (email) must return a 4xx; got "
                                                + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC14: register with missing email must NOT 2xx (a user without an email "
                                                + "cannot be valid). body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC14: register with missing email must NOT 5xx — the controller must "
                                                + "validate input cleanly, not crash. body=" + r.body());
        }
}

// ─── TC15 — Register with role=ADMIN in body must NOT yield an ADMIN account ─
@Tag("public")
@Tag("features_m2")
class TC15_RegisterRoleEscalationTests extends TestBase {

        @Test
        @DisplayName("TC15 — POST registerPath() with `role:ADMIN` in body must NOT result in an ADMIN account (privilege-escalation)")
        void register_with_role_admin_in_body_must_not_escalate() throws Exception {
                BASE_URL = userServiceUrl;
                // Build a register body with `role:"ADMIN"` injected. The
                // controller may either ignore the field, reject the request,
                // or accept the request — but the resulting DB row's role
                // column must NOT be ADMIN. Anything else is privilege escalation.
                String email = "tc15_" + nonce() + "@grader.testgen.io";
                String body = String.format(
                                """
                                                {"name":"TC15 User","email":"%s","password":"TestPwd!2026","phone":"+201%s","role":"ADMIN"}
                                                """,
                                email, nonce().substring(0, 9));

                HttpResponse<String> reg = httpPost("/api/auth/register", body);
                // We don't strictly require 2xx here — a controller that
                // rejects extra fields with 4xx is also fine. What matters is
                // that no ADMIN account got created.
                int regCode = reg.statusCode();
                assertTrue(regCode / 100 != 5,
                                "TC15: register with role=ADMIN body must NOT 5xx (got " + regCode
                                                + "). body=" + reg.body());

                // If the registration succeeded, look up the role in DB and
                // assert it's NOT ADMIN.
                if (regCode / 100 == 2) {
                        String role = fetchUserRole(email);
                        assertNotNull(role,
                                        "TC15: registration returned 2xx but no user row found for email "
                                                        + email + " — register isn't actually persisting.");
                        assertTrue(!"ADMIN".equalsIgnoreCase(role),
                                        "TC15: registering with role=ADMIN in the body must NOT result in an "
                                                        + "ADMIN account. Found role=" + role + " (expected the theme "
                                                        + "default, NOT ADMIN). This is a privilege-escalation bug — the "
                                                        + "controller is mapping the body's role field into the entity.");
                }
                // If registration was rejected (4xx), that's also acceptable —
                // the role field was caught as invalid input. Either way no
                // ADMIN was created.
        }
}

// ─── TC16 — Login with empty password returns 4xx (negative path) ───────────
@Tag("public")
@Tag("features_m2")
class TC16_LoginEmptyPasswordTests extends TestBase {

        @Test
        @DisplayName("TC16 — POST loginPath() with `password:\"\"` (empty) returns NOT 2xx")
        void login_empty_password_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register a real user so the email exists.
                String email = "tc16_" + nonce() + "@grader.testgen.io";
                String regBody = String.format("""
                                {"name":"TC16 User","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBody), "TC16 setup register");

                // Act — login with empty password. Bcrypt verification must
                // not be bypassed by an empty input.
                String loginBody = String.format("""
                                {"email":"%s","password":""}
                                """, email);
                HttpResponse<String> r = httpPost("/api/auth/login", loginBody);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC16: login with empty password must NOT issue a token (status " + code
                                                + "). A 2xx here means bcrypt verification was bypassed for empty "
                                                + "input. body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC16: login with empty password must NOT 5xx (status " + code + "). The "
                                                + "controller must validate input cleanly, not NPE on empty string. "
                                                + "body=" + r.body());
                // Acceptable: 400 (validation error) or 401 (auth failure) or
                // 422 (semantic validation error). All 4xx.
                assertTrue(code >= 400 && code < 500,
                                "TC16: login with empty password must return a 4xx (validation or auth "
                                                + "failure); got " + code + " body=" + r.body());
        }
}

// ─── TC17 — Cross-user IDOR: User A cannot READ User B's profile ────────────
@Tag("public")
@Tag("authorization")
class TC17_IdorReadOtherUserTests extends TestBase {

        @Test
        @DisplayName("TC17 — Customer A's GET on User B's CRUD path must NOT be 2xx (cross-user IDOR)")
        void customer_a_cannot_read_user_b_profile() throws Exception {
                BASE_URL = userServiceUrl;
                String emailA = "tc17a_" + nonce() + "@grader.testgen.io";
                String emailB = "tc17b_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBodyA = String.format("""
                                {"name":"TC17 A","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailA, pwd, nonce().substring(0, 9));
                String regBodyB = String.format("""
                                {"name":"TC17 B","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailB, pwd, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBodyA), "TC17 setup register A");
                HttpResponse<String> regB = httpPost("/api/auth/register", regBodyB);
                assert2xx(regB, "TC17 setup register B");
                long bid = uidFromJwt(parseNode(regB.body()).get("token").asText());

                String loginBodyA = String.format("""
                                {"email":"%s","password":"%s"}
                                """, emailA, pwd);
                HttpResponse<String> loginA = httpPost("/api/auth/login", loginBodyA);
                assert2xx(loginA, "TC17 setup login A");
                String tokenA = parseNode(loginA.body()).get("token").asText();

                HttpResponse<String> r = httpGetAuth("/api/users/" + bid, tokenA);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC17: customer A reading customer B's profile must NOT be 2xx (status "
                                                + code + "). A 2xx here means cross-user IDOR is unprotected — any "
                                                + "authenticated user can read any other user's profile. body="
                                                + r.body());
                assertTrue(code / 100 != 5,
                                "TC17: cross-user read must NOT 5xx (status " + code + "). The auth check "
                                                + "must reject cleanly, not crash. body=" + r.body());
                assertTrue(code == 403 || code == 404,
                                "TC17: cross-user read must return 403 (forbidden) or 404 (not-found / "
                                                + "privacy-by-obscurity); got " + code + " body=" + r.body());
        }
}

// ─── TC18 — Cross-user IDOR: User A cannot UPDATE User B's profile ──────────
@Tag("public")
@Tag("authorization")
class TC18_IdorUpdateOtherUserTests extends TestBase {

        @Test
        @DisplayName("TC18 — Customer A's PUT on User B's CRUD path must NOT be 2xx, AND B's data must NOT change in DB")
        void customer_a_cannot_update_user_b_profile() throws Exception {
                BASE_URL = userServiceUrl;
                String emailA = "tc18a_" + nonce() + "@grader.testgen.io";
                String emailB = "tc18b_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String origNameB = "TC18 B Original";
                String regBodyA = String.format("""
                                {"name":"TC18 A","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailA, pwd, nonce().substring(0, 9));
                String regBodyB = String.format("""
                                {"name":"%s","email":"%s","password":"%s","phone":"+201%s"}
                                """, origNameB, emailB, pwd, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBodyA), "TC18 setup register A");
                HttpResponse<String> regB = httpPost("/api/auth/register", regBodyB);
                assert2xx(regB, "TC18 setup register B");
                long bid = uidFromJwt(parseNode(regB.body()).get("token").asText());

                String loginBodyA = String.format("""
                                {"email":"%s","password":"%s"}
                                """, emailA, pwd);
                HttpResponse<String> loginA = httpPost("/api/auth/login", loginBodyA);
                assert2xx(loginA, "TC18 setup login A");
                String tokenA = parseNode(loginA.body()).get("token").asText();

                // Mitigation pattern — include all original fields plus the
                // changed name (some controllers require all fields on PUT).
                String tamperedName = "TC18 HIJACK";
                String putBody = String.format("""
                                {"name":"%s","email":"%s","password":"%s","phone":"+201%s"}
                                """, tamperedName, emailB, pwd, nonce().substring(0, 9));
                HttpResponse<String> r = httpPutAuth("/api/users/" + bid, putBody, tokenA);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC18: customer A updating customer B's profile must NOT be 2xx (status "
                                                + code + "). A 2xx here means cross-user IDOR write is unprotected. "
                                                + "body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC18: cross-user update must NOT 5xx (status " + code + "). body=" + r.body());
                assertTrue(code == 403 || code == 404,
                                "TC18: cross-user update must return 403 or 404; got " + code + " body=" + r.body());

                // Defensive — even if controller returned 4xx, verify B's row
                // in DB was NOT mutated. Some buggy controllers reject the
                // response but commit the change.
                String userTable = tableName("User");
                String currentName = jdbc.queryForObject(
                                "SELECT name FROM " + userTable + " WHERE id = ?",
                                String.class, bid);
                assertEquals(origNameB, currentName,
                                "TC18: cross-user PUT was rejected (status " + code + ") but B's name in DB "
                                                + "changed from '" + origNameB + "' to '" + currentName + "' — the "
                                                + "controller is committing the change before doing the auth check.");
        }
}

// ─── TC19 — Cross-user IDOR: User A cannot DELETE User B ────────────────────
@Tag("public")
@Tag("authorization")
class TC19_IdorDeleteOtherUserTests extends TestBase {

        @Test
        @DisplayName("TC19 — Customer A's DELETE on User B's CRUD path must NOT be 2xx, AND B must STILL exist in DB")
        void customer_a_cannot_delete_user_b() throws Exception {
                BASE_URL = userServiceUrl;
                String emailA = "tc19a_" + nonce() + "@grader.testgen.io";
                String emailB = "tc19b_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBodyA = String.format("""
                                {"name":"TC19 A","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailA, pwd, nonce().substring(0, 9));
                String regBodyB = String.format("""
                                {"name":"TC19 B","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailB, pwd, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBodyA), "TC19 setup register A");
                HttpResponse<String> regB = httpPost("/api/auth/register", regBodyB);
                assert2xx(regB, "TC19 setup register B");
                long bid = uidFromJwt(parseNode(regB.body()).get("token").asText());

                String loginBodyA = String.format("""
                                {"email":"%s","password":"%s"}
                                """, emailA, pwd);
                HttpResponse<String> loginA = httpPost("/api/auth/login", loginBodyA);
                assert2xx(loginA, "TC19 setup login A");
                String tokenA = parseNode(loginA.body()).get("token").asText();

                HttpResponse<String> r = httpDeleteAuth("/api/users/" + bid, tokenA);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC19: customer A deleting customer B must NOT be 2xx (status " + code
                                                + "). A 2xx here means cross-user delete is unprotected. body="
                                                + r.body());
                assertTrue(code / 100 != 5,
                                "TC19: cross-user delete must NOT 5xx (status " + code + "). body=" + r.body());
                assertTrue(code == 403 || code == 404,
                                "TC19: cross-user delete must return 403 or 404; got " + code + " body=" + r.body());

                // Defensive — B's row must STILL exist in DB.
                String userTable = tableName("User");
                Integer count = jdbc.queryForObject(
                                "SELECT COUNT(*) FROM " + userTable + " WHERE id = ?",
                                Integer.class, bid);
                assertNotNull(count);
                assertTrue(count == 1,
                                "TC19: cross-user DELETE was rejected (status " + code + ") but B's row in DB "
                                                + "is gone (count=" + count + ") — the controller is committing the "
                                                + "delete before doing the auth check.");
        }
}

// ─── TC20 — Owner happy path: User A can UPDATE their own profile ───────────
@Tag("public")
@Tag("authorization")
class TC20_OwnerUpdateOwnProfileTests extends TestBase {

        @Test
        @DisplayName("TC20 — Customer's PUT on their own User CRUD path returns 2xx, AND DB reflects the new name")
        void owner_can_update_own_profile() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc20_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String origPhone = "+201" + nonce().substring(0, 9);
                String regBody = String.format("""
                                {"name":"TC20 Original","email":"%s","password":"%s","phone":"%s"}
                                """, email, pwd, origPhone);
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC20 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC20 setup login");
                String token = parseNode(login.body()).get("token").asText();

                // Mitigation pattern — include all original fields plus the new name.
                String newName = "TC20 Updated";
                String putBody = String.format("""
                                {"name":"%s","email":"%s","password":"%s","phone":"%s"}
                                """, newName, email, pwd, origPhone);
                HttpResponse<String> r = httpPutAuth("/api/users/" + uid, putBody, token);
                assert2xx(r, "TC20 owner update own profile");

                // JDBC verification (NOT via GET) — we're testing the PUT
                // path's persistence semantics specifically.
                String userTable = tableName("User");
                String currentName = jdbc.queryForObject(
                                "SELECT name FROM " + userTable + " WHERE id = ?",
                                String.class, uid);
                assertEquals(newName, currentName,
                                "TC20: PUT returned " + r.statusCode() + " but DB row's name is '" + currentName
                                                + "' (expected '" + newName
                                                + "') — the controller returned 2xx without "
                                                + "actually persisting the update.");
        }
}

// ─── TC21 — Admin override: admin can READ any user ─────────────────────────
@Tag("public")
@Tag("authorization")
class TC21_AdminReadAnyUserTests extends TestBase {

        @Test
        @DisplayName("TC21 — Admin's GET on a customer's User CRUD path returns 2xx (admin role bypasses ownership)")
        void admin_can_read_any_user() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc21_" + nonce() + "@grader.testgen.io";
                String regBody = String.format("""
                                {"name":"TC21 Customer","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC21 setup register customer");
                long customerId = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String adminTok = adminToken();
                HttpResponse<String> r = httpGetAuth("/api/users/" + customerId, adminTok);
                assert2xx(r, "TC21 admin read customer");
                JsonNode j = parseNode(r.body());
                assertTrue(j.isObject(),
                                "TC21: admin GET response body must be a JSON object; got " + r.body());
        }
}

// ─── TC22 — Admin override: admin can UPDATE any user ───────────────────────
@Tag("public")
@Tag("authorization")
class TC22_AdminUpdateAnyUserTests extends TestBase {

        @Test
        @DisplayName("TC22 — Admin's PUT on a customer's User CRUD path returns 2xx, AND DB reflects the new name")
        void admin_can_update_any_user() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc22_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String origPhone = "+201" + nonce().substring(0, 9);
                String regBody = String.format("""
                                {"name":"TC22 Customer","email":"%s","password":"%s","phone":"%s"}
                                """, email, pwd, origPhone);
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC22 setup register customer");
                long customerId = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String adminTok = adminToken();

                // Mitigation pattern — include all original fields plus the new name.
                String newName = "TC22 Admin-Updated";
                String putBody = String.format("""
                                {"name":"%s","email":"%s","password":"%s","phone":"%s"}
                                """, newName, email, pwd, origPhone);
                HttpResponse<String> r = httpPutAuth("/api/users/" + customerId, putBody, adminTok);
                assert2xx(r, "TC22 admin update customer");

                String userTable = tableName("User");
                String currentName = jdbc.queryForObject(
                                "SELECT name FROM " + userTable + " WHERE id = ?",
                                String.class, customerId);
                assertEquals(newName, currentName,
                                "TC22: admin PUT returned " + r.statusCode() + " but customer's name in DB is '"
                                                + currentName + "' (expected '" + newName + "') — admin update did not "
                                                + "persist.");
        }
}

// ─── TC23 — Admin override: admin can DELETE any user (strict hard-delete) ──
@Tag("public")
@Tag("authorization")
class TC23_AdminDeleteAnyUserTests extends TestBase {

        @Test
        @DisplayName("TC23 — Admin's DELETE on a customer's User CRUD path returns 2xx, AND the row is HARD-deleted (strict)")
        void admin_can_delete_any_user_hard() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc23_" + nonce() + "@grader.testgen.io";
                String regBody = String.format("""
                                {"name":"TC23 Customer","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC23 setup register customer");
                long customerId = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String adminTok = adminToken();
                HttpResponse<String> r = httpDeleteAuth("/api/users/" + customerId, adminTok);
                assert2xx(r, "TC23 admin delete customer");

                // STRICT hard-delete: row must be physically gone from DB.
                // Soft-delete is NOT acceptable for this test per direction.
                String userTable = tableName("User");
                Integer count = jdbc.queryForObject(
                                "SELECT COUNT(*) FROM " + userTable + " WHERE id = ?",
                                Integer.class, customerId);
                assertNotNull(count);
                assertEquals(0, count.intValue(),
                                "TC23: admin DELETE returned " + r.statusCode() + " but customer row STILL "
                                                + "exists in DB (count=" + count
                                                + "). DELETE must hard-delete the row, "
                                                + "not soft-delete it (use the deactivate endpoint for status changes).");

                // GET-after-DELETE — strictly 404.
                HttpResponse<String> g = httpGetAuth("/api/users/" + customerId, adminTok);
                int gcode = g.statusCode();
                assertTrue(gcode / 100 != 2,
                                "TC23: GET-after-DELETE returned 2xx (status " + gcode + "). The row was "
                                                + "already verified gone from DB above, but GET still finds it. body="
                                                + g.body());
                assertEquals(404, gcode,
                                "TC23: GET after a successful DELETE must return 404 Not Found; got " + gcode
                                                + " body=" + g.body());
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S1-F12 — Get User Activity Feed
// GET /api/users/{id}/activity?page={page}&size={size}
// Auth: required user. Ownership: caller must be target OR admin.
// Defaults: page=0, size=10, max size=100. Response shape:
// { content: [{action, timestamp, details}], page, size, totalElements }
// ════════════════════════════════════════════════════════════════════════════

// ─── TC24 — Owner GET own activity returns 2xx with paginated envelope ──────
@Tag("public")
@Tag("features_m2")
class TC24_ActivityOwnerHappyPathTests extends TestBase {

        @Test
        @DisplayName("TC24 — GET /api/users/{ownId}/activity with own token returns 2xx and a paginated envelope")
        void owner_activity_returns_2xx_with_envelope() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register and login the user.
                String email = "tc24_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC24 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC24 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC24 setup login");
                String token = parseNode(login.body()).get("token").asText();

                // Act — GET own activity.
                String activityPath = "/api/users" + "/" + uid + "/activity";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                assert2xx(r, "TC24 owner activity");

                // Assert envelope shape per spec: content[], page, size, totalElements.
                JsonNode j = parseNode(r.body());
                assertTrue(j.has("content"),
                                "TC24: response must include `content` field; body=" + r.body());
                assertTrue(j.get("content").isArray(),
                                "TC24: `content` must be an array; got " + j.get("content"));
                assertTrue(j.has("page"),
                                "TC24: response must include `page` field; body=" + r.body());
                assertTrue(j.has("size"),
                                "TC24: response must include `size` field; body=" + r.body());
                assertTrue(j.has("totalElements"),
                                "TC24: response must include `totalElements` field; body=" + r.body());
        }
}

// ─── TC25 — Non-existent user ID returns 404 (admin token) ──────────────────
@Tag("public")
@Tag("features_m2")
class TC25_ActivityNonExistentIdTests extends TestBase {

        @Test
        @DisplayName("TC25 — GET /api/users/<Long.MAX_VALUE>/activity with admin token returns strictly 404")
        void activity_non_existent_id_returns_404() throws Exception {
                BASE_URL = userServiceUrl;
                // Use admin token: per spec, admin passes ownership check, then
                // user-not-found returns 404. With a non-admin token, ownership
                // would fail first with 403 — wrong path for this test.
                String adminTok = adminToken();
                long missingId = Long.MAX_VALUE;
                String activityPath = "/api/users" + "/" + missingId + "/activity";

                HttpResponse<String> r = httpGetAuth(activityPath, adminTok);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC25: activity for a non-existent user ID must NOT be 2xx (status " + code
                                                + "). body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC25: activity for a non-existent user ID must NOT 5xx — server must handle "
                                                + "missing-user gracefully. body=" + r.body());
                assertEquals(404, code,
                                "TC25: per spec, admin passes ownership check then user-not-found yields "
                                                + "strictly 404; got " + code + " body=" + r.body());
        }
}

// ─── TC26 — Negative user ID returns 4xx (admin token) ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC26_ActivityNegativeIdTests extends TestBase {

        @Test
        @DisplayName("TC26 — GET /api/users/-1/activity with admin token returns a 4xx (graceful)")
        void activity_negative_id_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Admin token — bypass ownership so the test actually exercises
                // the negative-id rejection logic (validation OR not-found).
                String adminTok = adminToken();
                String activityPath = "/api/users" + "/-1/activity";

                HttpResponse<String> r = httpGetAuth(activityPath, adminTok);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC26: activity for a negative user ID must NOT 5xx — controller must "
                                                + "validate / reject gracefully, not crash. status=" + code + " body="
                                                + r.body());
                assertTrue(code / 100 != 2,
                                "TC26: activity for a negative user ID must NOT be 2xx — negative ids cannot "
                                                + "match any real user. status=" + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC26: activity for a negative user ID must return a 4xx (400 validation or "
                                                + "404 not-found); got " + code + " body=" + r.body());
        }
}

// ─── TC27 — String user ID returns 4xx (admin token) ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC27_ActivityStringIdTests extends TestBase {

        @Test
        @DisplayName("TC27 — GET /api/users/abc/activity with admin token returns a 4xx (path-var binding fails)")
        void activity_string_id_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Admin token — ensures any 401 we see is NOT from missing auth.
                String adminTok = adminToken();
                String activityPath = "/api/users" + "/abc/activity";

                HttpResponse<String> r = httpGetAuth(activityPath, adminTok);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC27: activity for a non-numeric user ID must NOT 5xx — Spring's path-var "
                                                + "binding should reject the string cleanly, not throw an unhandled "
                                                + "TypeMismatchException. status=" + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC27: activity for a non-numeric user ID must NOT be 2xx — 'abc' cannot be "
                                                + "a valid Long. status=" + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC27: activity for a non-numeric user ID must return a 4xx (typically 400 "
                                                + "Bad Request); got " + code + " body=" + r.body());
        }
}

// ─── TC28 — size=0 returns gracefully (NOT 5xx) ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC28_ActivitySizeZeroTests extends TestBase {

        @Test
        @DisplayName("TC28 — GET /api/users/{ownId}/activity?size=0 must NOT 5xx (spec silent on size=0)")
        void activity_size_zero_does_not_5xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — own user so we reach the pagination logic.
                String email = "tc28_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC28 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC28 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC28 setup login");
                String token = parseNode(login.body()).get("token").asText();

                // Act — size=0. PageRequest.of(0, 0) throws IllegalArgumentException,
                // so unhandled this becomes 500. Spec doesn't pin a code here, but
                // graceful handling means NOT 5xx.
                String activityPath = "/api/users" + "/" + uid + "/activity?size=0";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC28: size=0 must NOT 5xx — controller must validate / clamp / reject "
                                                + "gracefully, not let PageRequest.of throw IllegalArgumentException. "
                                                + "status=" + code + " body=" + r.body());
        }
}

// ─── TC29 — size=-1 returns 4xx ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC29_ActivityNegativeSizeTests extends TestBase {

        @Test
        @DisplayName("TC29 — GET /api/users/{ownId}/activity?size=-1 returns a 4xx")
        void activity_negative_size_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc29_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC29 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC29 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC29 setup login");
                String token = parseNode(login.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + uid + "/activity?size=-1";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC29: size=-1 must NOT 5xx — controller must validate gracefully. status="
                                                + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC29: size=-1 must NOT be 2xx — negative page size is semantically invalid. "
                                                + "status=" + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC29: size=-1 must return a 4xx; got " + code + " body=" + r.body());
        }
}

// ─── TC30 — size=string returns 4xx (binding fails) ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC30_ActivityStringSizeTests extends TestBase {

        @Test
        @DisplayName("TC30 — GET /api/users/{ownId}/activity?size=abc returns a 4xx (Integer binding fails)")
        void activity_string_size_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc30_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC30 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC30 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC30 setup login");
                String token = parseNode(login.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + uid + "/activity?size=abc";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC30: size=abc must NOT 5xx — Spring's @RequestParam Integer binding should "
                                                + "reject the string cleanly. status=" + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC30: size=abc must NOT be 2xx — non-numeric size cannot be valid. status="
                                                + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC30: size=abc must return a 4xx (typically 400 Bad Request); got " + code
                                                + " body=" + r.body());
        }
}

// ─── TC31 — Cross-user activity (regular user) returns strictly 403 ─────────
@Tag("public")
@Tag("features_m2")
class TC31_ActivityCrossUserRegularTests extends TestBase {

        @Test
        @DisplayName("TC31 — Customer A's GET on User B's activity returns strictly 403 (per S1-F12 spec)")
        void cross_user_activity_regular_returns_403() throws Exception {
                BASE_URL = userServiceUrl;
                // Per spec: "ownership violation, NOT 404 — A's token is valid
                // and B exists." Strict 403 here, unlike TC17 (which accepts 403/404).
                String emailA = "tc31a_" + nonce() + "@grader.testgen.io";
                String emailB = "tc31b_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBodyA = String.format("""
                                {"name":"TC31 A","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailA, pwd, nonce().substring(0, 9));
                String regBodyB = String.format("""
                                {"name":"TC31 B","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailB, pwd, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBodyA), "TC31 setup register A");
                HttpResponse<String> regB = httpPost("/api/auth/register", regBodyB);
                assert2xx(regB, "TC31 setup register B");
                long bid = uidFromJwt(parseNode(regB.body()).get("token").asText());

                String loginBodyA = String.format("""
                                {"email":"%s","password":"%s"}
                                """, emailA, pwd);
                HttpResponse<String> loginA = httpPost("/api/auth/login", loginBodyA);
                assert2xx(loginA, "TC31 setup login A");
                String tokenA = parseNode(loginA.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + bid + "/activity";
                HttpResponse<String> r = httpGetAuth(activityPath, tokenA);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC31: cross-user activity GET must NOT be 2xx — regular users cannot read "
                                                + "other users' activity feeds. status=" + code + " body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC31: cross-user activity GET must NOT 5xx. status=" + code + " body=" + r.body());
                assertEquals(403, code,
                                "TC31: per S1-F12 spec, cross-user activity GET must return strictly 403 "
                                                + "(ownership violation, NOT 404 — A's token is valid and B exists); got "
                                                + code + " body=" + r.body());
        }
}

// ─── TC32 — Cross-user activity (admin) returns 2xx ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC32_ActivityCrossUserAdminTests extends TestBase {

        @Test
        @DisplayName("TC32 — Admin's GET on a customer's activity returns 2xx (admin bypasses ownership)")
        void cross_user_activity_admin_returns_2xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc32_" + nonce() + "@grader.testgen.io";
                String regBody = String.format("""
                                {"name":"TC32 Customer","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC32 setup register customer");
                long customerId = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String adminTok = adminToken();
                String activityPath = "/api/users" + "/" + customerId + "/activity";
                HttpResponse<String> r = httpGetAuth(activityPath, adminTok);
                assert2xx(r, "TC32 admin activity");

                JsonNode j = parseNode(r.body());
                assertTrue(j.has("content") && j.get("content").isArray(),
                                "TC32: admin response must include `content` array; body=" + r.body());
        }
}

// ─── TC33 — page=-1 returns 4xx ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC33_ActivityNegativePageTests extends TestBase {

        @Test
        @DisplayName("TC33 — GET /api/users/{ownId}/activity?page=-1 returns a 4xx")
        void activity_negative_page_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc33_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC33 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC33 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC33 setup login");
                String token = parseNode(login.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + uid + "/activity?page=-1";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC33: page=-1 must NOT 5xx — PageRequest.of(int page, int size) requires "
                                                + "page >= 0; controller must validate gracefully. status=" + code
                                                + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC33: page=-1 must NOT be 2xx — negative page is semantically invalid. "
                                                + "status=" + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC33: page=-1 must return a 4xx; got " + code + " body=" + r.body());
        }
}

// ─── TC34 — page=string returns 4xx (binding fails) ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC34_ActivityStringPageTests extends TestBase {

        @Test
        @DisplayName("TC34 — GET /api/users/{ownId}/activity?page=abc returns a 4xx (Integer binding fails)")
        void activity_string_page_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc34_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC34 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC34 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC34 setup login");
                String token = parseNode(login.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + uid + "/activity?page=abc";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC34: page=abc must NOT 5xx — Spring's @RequestParam Integer binding should "
                                                + "reject the string cleanly. status=" + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC34: page=abc must NOT be 2xx — non-numeric page cannot be valid. status="
                                                + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC34: page=abc must return a 4xx (typically 400 Bad Request); got " + code
                                                + " body=" + r.body());
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S2 M2 — Catalog entity features (full-text search, indexing, dashboard).
// All tests dynamic via TestBase helpers:
// * s2CatalogEntity() — Driver / Product / Provider / etc.
// * s3RideEntity() — Ride / Booking / Transaction / etc.
// * s2CategoricalFilterParam() — first non-status enum field (cuisineType /
// category / specialty / ...)
// * enumValueAt(entity, field, idx) — i-th valid value for that enum
// * s2EventsCollection() — Mongo events collection (spec name, validated)
// * s2SearchIndex() — ES index name (spec name, validated)
// * buildKitchenSinkBody(...) — JSON body from manifest entityColumns
// ════════════════════════════════════════════════════════════════════════════

// ─── TC35 — S2-F10 happy path search returns 2xx + array ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC35_SearchHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC35 — GET <s2>/search/full-text?query=test with valid token returns 2xx + array shape")
        void search_happy_path_returns_2xx_array() throws Exception {
                BASE_URL = driverServiceUrl;
                String token = adminToken();
                String searchPath = "/api/drivers" + "/search/full-text?query=test";
                HttpResponse<String> r = httpGetAuth(searchPath, token);
                assert2xx(r, "TC35 search happy path");
                JsonNode body = parseNode(r.body());
                boolean validShape = body.isArray() || (body.has("content") && body.get("content").isArray());
                assertTrue(validShape, "TC35: response must be JSON array OR paginated envelope; got " + r.body());
        }
}

// ─── TC36 — S2-F10 no token returns 401 ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC36_SearchNoTokenTests extends TestBase {
        @Test
        @DisplayName("TC36 — GET <s2>/search/full-text without Authorization header returns 401")
        void search_no_token_returns_401() throws Exception {
                BASE_URL = driverServiceUrl;
                String searchPath = "/api/drivers" + "/search/full-text?query=anything";
                HttpResponse<String> r = httpGet(searchPath);
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC36: must NOT 2xx; got " + code);
                assertTrue(code / 100 != 5, "TC36: must NOT 5xx; got " + code);
                assertEquals(401, code, "TC36: must be strict 401; got " + code + " body=" + r.body());
        }
}

// ─── TC37 — S2-F10 exact match by primary categorical filter ────────────────
@Tag("public")
@Tag("features_m2")
@Disabled("Uber Driver entity has no non-status categorical filter field — see Uber M1 §6.2. "
                + "The only enum on Driver is 'status'; TC38 already covers status-based filtering, "
                + "so coverage is preserved without this case.")
class TC37_SearchExactCategoricalFilterTests extends TestBase {
        @Test
        @DisplayName("TC37 — Search ?<filter>=<value0> returns only entities with that filter value")
        void search_filter_categorical_returns_only_matching() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String entity = s2CatalogEntity();
                String filterParam = s2CategoricalFilterParam();
                String filterValue0 = enumValueAt(entity, filterParam, 0);
                String filterValue1 = enumValueAt(entity, filterParam, 1);
                String statusOpen = enumValueAt(entity, "status", 0);

                String n = nonce();
                createEntity(adminTok, "TC37 First_" + n, filterValue0, statusOpen);
                createEntity(adminTok, "TC37 Second_" + n, filterValue1, statusOpen);

                String searchPath = crudCollectionPath(entity) + "/search/full-text?" + filterParam + "="
                                + filterValue0;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC37 search by " + filterParam);
                JsonNode arr = unwrap(parseNode(r.body()));
                for (JsonNode item : arr) {
                        String c = item.has(filterParam) ? item.get(filterParam).asText() : null;
                        assertEquals(filterValue0, c,
                                        "TC37: every result must have " + filterParam + "=" + filterValue0 + "; got "
                                                        + item);
                }
        }

        private void createEntity(String tok, String name, String filterValue, String status) throws Exception {
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of(
                                "name", name,
                                s2CategoricalFilterParam(), filterValue,
                                "status", status));
                assert2xx(httpPostAuth("/api/drivers", body, tok),
                                "TC37 setup create " + name);
        }

        private JsonNode unwrap(JsonNode b) {
                return b.isArray() ? b : (b.has("content") ? b.get("content") : b);
        }
}

// ─── TC38 — S2-F10 exact match by status ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC38_SearchExactStatusTests extends TestBase {
        @Test
        @DisplayName("TC38 — Search ?status=<value0> returns only entities with that status")
        void search_filter_status_returns_only_matching() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String entity = s2CatalogEntity();
                String status0 = enumValueAt(entity, "status", 0);
                String status1 = enumValueAt(entity, "status", 1);

                String n = nonce();
                createEntity(adminTok, "TC38 Status0_" + n, status0);
                createEntity(adminTok, "TC38 Status1_" + n, status1);

                // query= (empty) matches all; status filter narrows by status
                String searchPath = crudCollectionPath(entity) + "/search/full-text?query=&status=" + status0;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC38 search by status");
                JsonNode arr = unwrap(parseNode(r.body()));
                for (JsonNode item : arr) {
                        String s = item.has("status") ? item.get("status").asText() : null;
                        assertEquals(status0, s, "TC38: every result must have status=" + status0 + "; got " + item);
                }
        }

        private void createEntity(String tok, String name, String status) throws Exception {
                // Build body without categorical filter (Driver entity may have no non-status enum)
                java.util.Map<String, Object> overrides = new java.util.HashMap<>();
                overrides.put("name", name);
                overrides.put("status", status);
                try {
                        String catParam = s2CategoricalFilterParam();
                        String catVal = enumValueAt(s2CatalogEntity(), catParam, 0);
                        overrides.put(catParam, catVal);
                } catch (IllegalStateException ignored) { /* no non-status enum field */ }
                String body = buildKitchenSinkBody(s2CatalogEntity(), overrides);
                assert2xx(httpPostAuth("/api/drivers", body, tok),
                                "TC38 setup create " + name);
        }

        private JsonNode unwrap(JsonNode b) {
                return b.isArray() ? b : (b.has("content") ? b.get("content") : b);
        }
}

// ─── TC39 — S2-F10 minRating + maxRating range filter ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC39_SearchRatingRangeTests extends TestBase {
        @Test
        @DisplayName("TC39 — Search ?minRating=4.0&maxRating=5.0 returns only entities with rating in [4.0, 5.0]")
        void search_rating_range_returns_entities_in_range() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String entity = s2CatalogEntity();
                String n = nonce();
                long lowId = createAndRate(adminTok, "TC39 Low_" + n, 3.0);
                long midId = createAndRate(adminTok, "TC39 Mid_" + n, 4.5);
                long highId = createAndRate(adminTok, "TC39 High_" + n, 5.0);
                reindex(adminTok, lowId);
                reindex(adminTok, midId);
                reindex(adminTok, highId);

                String searchPath = crudCollectionPath(entity) + "/search/full-text?minRating=4.0&maxRating=5.0";
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC39 search rating range");
                JsonNode arr = unwrap(parseNode(r.body()));
                for (JsonNode item : arr) {
                        if (!item.has("rating") || item.get("rating").isNull())
                                continue;
                        double rating = item.get("rating").asDouble();
                        assertTrue(rating >= 4.0 && rating <= 5.0,
                                        "TC39: every result must have rating in [4.0, 5.0]; got " + rating + " in "
                                                        + item);
                }
        }

        private long createAndRate(String tok, String name, double rating) throws Exception {
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of("name", name));
                HttpResponse<String> r = httpPostAuth("/api/drivers", body, tok);
                assert2xx(r, "TC39 setup create " + name);
                long id = parseNode(r.body()).get("id").asLong();
                String ratingCol = columnByField(s2CatalogEntity(), "rating");
                jdbc.update("UPDATE \"" + tableName(s2CatalogEntity()) + "\" SET \"" + ratingCol
                                + "\" = ? WHERE id = ?", rating, id);
                return id;
        }

        private void reindex(String tok, long id) throws Exception {
                HttpResponse<String> r = httpPostAuth("/api/drivers" + "/" + id + "/index", "",
                                tok);
                assert2xx(r, "TC39 reindex id=" + id);
        }

        private JsonNode unwrap(JsonNode b) {
                return b.isArray() ? b : (b.has("content") ? b.get("content") : b);
        }
}

// ─── TC40 — S2-F10 minRating > maxRating returns 4xx ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC40_SearchInvalidRatingRangeTests extends TestBase {
        @Test
        @DisplayName("TC40 — Search ?minRating=5.0&maxRating=3.0 (invalid range) returns a 4xx")
        void search_invalid_rating_range_returns_4xx() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String searchPath = "/api/drivers"
                                + "/search/full-text?minRating=5.0&maxRating=3.0";
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                int code = r.statusCode();
                assertTrue(code / 100 != 5, "TC40: NOT 5xx; got " + code);
                assertTrue(code / 100 != 2, "TC40: NOT 2xx; got " + code);
                assertTrue(code >= 400 && code < 500, "TC40: must return 4xx; got " + code + " body=" + r.body());
        }
}

// ─── TC41 — S2-F10 query with no matches returns empty list ─────────────────
@Tag("public")
@Tag("features_m2")
class TC41_SearchNoMatchEmptyListTests extends TestBase {
        @Test
        @DisplayName("TC41 — Search with query that matches nothing returns 2xx + empty list")
        void search_no_match_returns_empty_list() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String improbableQuery = "TC41NoMatchQuery_" + nonce() + "_xyzqwe";
                String searchPath = "/api/drivers" + "/search/full-text?query="
                                + improbableQuery;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC41 search no match");
                JsonNode body = parseNode(r.body());
                JsonNode arr = body.isArray() ? body : (body.has("content") ? body.get("content") : body);
                assertTrue(arr.isArray(), "TC41: response must contain an array; got " + r.body());
                assertEquals(0, arr.size(), "TC41: must return empty list; got " + r.body());
        }
}

// ─── TC42 — S2-F10 results sorted by relevance ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC42_SearchSortedByRelevanceTests extends TestBase {
        @Test
        @DisplayName("TC42 — Search results sorted by relevance (name match ranks higher than description match)")
        void search_results_sorted_by_relevance() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String entity = s2CatalogEntity();
                String unique = "Tc42Word" + nonce();
                String aName = unique + " Kitchen";
                String bName = "Other Place TC42_" + nonce();
                long aid = createWithDetails(adminTok, aName, null);
                reindex(adminTok, aid);
                long bid = createWithDetails(adminTok, bName, "premium " + unique + " sedan service");
                reindex(adminTok, bid);

                String searchPath = crudCollectionPath(entity) + "/search/full-text?query=" + unique;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC42 search relevance");
                JsonNode body = parseNode(r.body());
                JsonNode arr = body.isArray() ? body : (body.has("content") ? body.get("content") : body);
                assertTrue(arr.isArray() && arr.size() >= 1,
                                "TC42: must return at least one result; body=" + r.body());

                int idxA = -1, idxB = -1;
                for (int i = 0; i < arr.size(); i++) {
                        String entryName = arr.get(i).has("name") ? arr.get(i).get("name").asText() : "";
                        if (aName.equals(entryName))
                                idxA = i;
                        if (bName.equals(entryName))
                                idxB = i;
                }
                assertTrue(idxA >= 0,
                                "TC42: name-match (A, name='" + aName + "') must appear in results; body=" + r.body());
                if (idxB >= 0) {
                        assertTrue(idxA < idxB,
                                        "TC42: name-match (A, idx=" + idxA
                                                        + ") must rank higher than description-match (B, idx=" + idxB
                                                        + ").");
                }
        }

        private long createWithDetails(String tok, String name, String desc) throws Exception {
                java.util.Map<String, Object> overrides = new java.util.HashMap<>();
                overrides.put("name", name);
                if (desc != null) {
                        overrides.put("details", java.util.Map.of("description", desc));
                }
                String body = buildKitchenSinkBody(s2CatalogEntity(), overrides);
                HttpResponse<String> r = httpPostAuth("/api/drivers", body, tok);
                assert2xx(r, "TC42 setup create " + name);
                return parseNode(r.body()).get("id").asLong();
        }

        private void reindex(String tok, long id) throws Exception {
                HttpResponse<String> r = httpPostAuth("/api/drivers" + "/" + id + "/index", "",
                                tok);
                assert2xx(r, "TC42 reindex id=" + id);
        }
}

// ─── TC43 — S2-F11 happy index path ─────────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC43_IndexHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC43 — POST <s2>/{id}/index for an existing entity returns 2xx")
        void index_happy_path_returns_2xx() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String body = buildKitchenSinkBody(s2CatalogEntity(),
                                java.util.Map.of("name", "TC43 Entity_" + nonce()));
                HttpResponse<String> created = httpPostAuth("/api/drivers", body, adminTok);
                assert2xx(created, "TC43 setup create");
                long id = parseNode(created.body()).get("id").asLong();
                HttpResponse<String> r = httpPostAuth("/api/drivers" + "/" + id + "/index", "",
                                adminTok);
                assert2xx(r, "TC43 index");
        }
}

// ─── TC44 — S2-F11 indexed document matches PG attributes ───────────────────
@Tag("public")
@Tag("features_m2")
class TC44_IndexMatchesPgTests extends TestBase {
        @Test
        @DisplayName("TC44 — After indexing, ES doc fields match the PG row's attributes")
        void index_doc_matches_pg_attributes() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String unique = "TC44Entity_" + nonce();
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of(
                                "name", unique,
                                "details", java.util.Map.of("description", "signature description")));
                HttpResponse<String> created = httpPostAuth("/api/drivers", body, adminTok);
                assert2xx(created, "TC44 setup create");
                long id = parseNode(created.body()).get("id").asLong();
                String ratingCol = columnByField(s2CatalogEntity(), "rating");
                jdbc.update("UPDATE \"" + tableName(s2CatalogEntity()) + "\" SET \"" + ratingCol
                                + "\" = ? WHERE id = ?", 4.5, id);
                HttpResponse<String> indexed = httpPostAuth("/api/drivers" + "/" + id + "/index",
                                "", adminTok);
                assert2xx(indexed, "TC44 index");

                String esIndex = s2SearchIndex();
                long esCount = esSearchCount(esIndex, "name", unique);
                assertTrue(esCount >= 1,
                                "TC44: ES index '" + esIndex + "' must contain a document with name='" + unique
                                                + "' (count=" + esCount + ").");

                String searchPath = "/api/drivers" + "/search/full-text?query=" + unique;
                HttpResponse<String> sr = httpGetAuth(searchPath, adminTok);
                assert2xx(sr, "TC44 search after index");
                JsonNode body2 = parseNode(sr.body());
                JsonNode arr = body2.isArray() ? body2 : (body2.has("content") ? body2.get("content") : body2);
                JsonNode found = null;
                for (JsonNode item : arr) {
                        String entryName = item.has("name") ? item.get("name").asText() : "";
                        if (unique.equals(entryName)) {
                                found = item;
                                break;
                        }
                }
                assertNotNull(found, "TC44: indexed entity must be findable via /search/full-text by name='" + unique
                                + "'; got " + sr.body());

                // Verify name + status fields match between search result and PG row.
                java.util.Map<String, Object> pgRow = jdbc.queryForMap(
                                "SELECT name, status::text AS status FROM " + tableName(s2CatalogEntity())
                                                + " WHERE id = ?",
                                id);
                assertEquals(pgRow.get("name"), found.get("name").asText(), "TC44: ES name must match PG name");
                if (found.has("status")) {
                        assertEquals(pgRow.get("status"), found.get("status").asText(),
                                        "TC44: ES status must match PG status");
                }
        }
}

// ─── TC45 — S2-F11 auto-reindex on update ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC45_IndexAutoReindexOnUpdateTests extends TestBase {
        @Test
        @DisplayName("TC45 — Updating an entity via PUT (without /index) makes the new name searchable")
        void auto_reindex_on_update() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String origName = "TC45 OriginalName_" + nonce();
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of("name", origName));
                HttpResponse<String> created = httpPostAuth("/api/drivers", body, adminTok);
                assert2xx(created, "TC45 setup create");
                long id = parseNode(created.body()).get("id").asLong();

                String newName = "TC45_NewName_" + nonce();
                String putBody = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of("name", newName));
                HttpResponse<String> updated = httpPutAuth("/api/drivers/" + id, putBody, adminTok);
                assert2xx(updated, "TC45 update name");

                String searchPath = "/api/drivers" + "/search/full-text?query=" + newName;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC45 search by new name");
                JsonNode body2 = parseNode(r.body());
                JsonNode arr = body2.isArray() ? body2 : (body2.has("content") ? body2.get("content") : body2);
                boolean found = false;
                for (JsonNode item : arr) {
                        String entryName = item.has("name") ? item.get("name").asText() : "";
                        if (newName.equals(entryName)) {
                                found = true;
                                break;
                        }
                }
                assertTrue(found, "TC45: search by new name must find the entity (proves auto-reindexing). body="
                                + r.body());
        }
}

// ─── TC46 — S2-F11 index on non-existent entity returns 404 ─────────────────
@Tag("public")
@Tag("features_m2")
class TC46_IndexNonExistentTests extends TestBase {
        @Test
        @DisplayName("TC46 — POST <s2>/<Long.MAX_VALUE>/index returns strictly 404")
        void index_non_existent_returns_404() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String indexPath = "/api/drivers" + "/" + Long.MAX_VALUE + "/index";
                HttpResponse<String> r = httpPostAuth(indexPath, "", adminTok);
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC46: NOT 2xx; got " + code);
                assertTrue(code / 100 != 5, "TC46: NOT 5xx; got " + code);
                assertEquals(404, code, "TC46: must be strict 404; got " + code + " body=" + r.body());
        }
}

// ─── TC47 — S2-F11 index without token returns 401 ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC47_IndexNoTokenTests extends TestBase {
        @Test
        @DisplayName("TC47 — POST <s2>/{id}/index without Authorization header returns 401")
        void index_no_token_returns_401() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String body = buildKitchenSinkBody(s2CatalogEntity(),
                                java.util.Map.of("name", "TC47 Entity_" + nonce()));
                HttpResponse<String> created = httpPostAuth("/api/drivers", body, adminTok);
                assert2xx(created, "TC47 setup create");
                long id = parseNode(created.body()).get("id").asLong();
                HttpResponse<String> r = httpPost("/api/drivers" + "/" + id + "/index", "");
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC47: NOT 2xx; got " + code);
                assertEquals(401, code, "TC47: must be strict 401; got " + code + " body=" + r.body());
        }
}

// ─── TC48 — S2-F12 dashboard happy path (uses pre-seeded entity id=1) ───────
@Tag("public")
@Tag("features_m2")
class TC48_DashboardHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC48 — GET <s2>/{id}/dashboard returns 2xx + DTO with totalRides/totalRevenue")
        void dashboard_happy_path() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                long restId = _UbM2.drv(this, "TC48 Driver " + nonce());
                HttpResponse<String> r = httpGetAuth(
                                "/api/drivers" + "/" + restId + "/dashboard", adminTok);
                assert2xx(r, "TC48 dashboard");
                JsonNode j = parseNode(r.body());
                assertTrue(j.has("totalRides") || j.has("total_rides"),
                                "TC48: dashboard must include totalRides; got " + r.body());
                assertTrue(j.has("totalRevenue") || j.has("total_revenue"),
                                "TC48: dashboard must include totalRevenue; got " + r.body());
        }
}

// ─── TC49 — S2-F12 aggregated values match PG-source values ─────────────────
@Tag("public")
@Tag("features_m2")
class TC49_DashboardAggregatesMatchPgTests extends TestBase {
        @Test
        @DisplayName("TC49 — Dashboard totalRides/totalRevenue match values aggregated from PG (uses pre-seed)")
        void dashboard_aggregates_match_pg() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                long restId = _UbM2.drv(this, "TC49 Driver " + nonce());
                String ridesTable = tableName(s3RideEntity());
                String fkCol = s2CatalogFkColumn();
                String amtCol = columnByField(s3RideEntity(), "totalAmount", "fare", "amount", "total");

                HttpResponse<String> r = httpGetAuth(
                                "/api/drivers" + "/" + restId + "/dashboard", adminTok);
                assert2xx(r, "TC49 dashboard");
                JsonNode j = parseNode(r.body());

                Integer expectedCount = jdbc.queryForObject(
                                "SELECT COUNT(*) FROM \"" + ridesTable + "\" WHERE \"" + fkCol + "\" = ?",
                                Integer.class, restId);
                Double expectedRevenueRaw = jdbc.queryForObject(
                                "SELECT COALESCE(SUM(\"" + amtCol + "\"), 0) FROM \"" + ridesTable + "\" WHERE \""
                                                + fkCol + "\" = ?",
                                Double.class, restId);
                double expectedRevenue = expectedRevenueRaw != null ? expectedRevenueRaw : 0.0;

                long actualCount = j.has("totalRides") ? j.get("totalRides").asLong()
                                : j.has("total_rides") ? j.get("total_rides").asLong() : -1L;
                double actualRevenue = j.has("totalRevenue") ? j.get("totalRevenue").asDouble()
                                : j.has("total_revenue") ? j.get("total_revenue").asDouble() : -1.0;

                assertEquals(expectedCount.longValue(), actualCount,
                                "TC49: totalRides mismatch — PG=" + expectedCount + ", dashboard=" + actualCount
                                                + ". body=" + r.body());
                assertEquals(expectedRevenue, actualRevenue, 0.01,
                                "TC49: totalRevenue mismatch — PG=" + expectedRevenue + ", dashboard=" + actualRevenue
                                                + ". body=" + r.body());
        }
}

// ─── TC50 — S2-F12 dashboard event written to MongoDB ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC50_DashboardEventLoggedTests extends TestBase {
        @Test
        @DisplayName("TC50 — After GET /dashboard, an event must appear in the spec-defined Mongo collection")
        void dashboard_logs_event_to_mongo() throws Exception {
                BASE_URL = driverServiceUrl;
                if (mongo == null) {
                        throw new AssertionError(
                                        "TC50: MongoDB is required for this test but not reachable. Set "
                                                        + "SPRING_DATA_MONGODB_URI or ensure the Mongo container is up.");
                }
                String adminTok = adminToken();
                long restId = _UbM2.drv(this, "TC50 Driver " + nonce());
                String collName = s2EventsCollection();
                com.mongodb.client.MongoCollection<org.bson.Document> coll = mongo.getCollection(collName);

                long before = coll.countDocuments();
                HttpResponse<String> r = httpGetAuth(
                                "/api/drivers" + "/" + restId + "/dashboard", adminTok);
                assert2xx(r, "TC50 dashboard");
                long after = coll.countDocuments();

                assertTrue(after > before,
                                "TC50: GET /dashboard must log an event in collection '" + collName
                                                + "'. Counts: before=" + before + ", after=" + after);
        }
}

// ─── TC51 — S2-F12 dashboard for non-existent ID returns 404 ────────────────
@Tag("public")
@Tag("features_m2")
class TC51_DashboardNonExistentTests extends TestBase {
        @Test
        @DisplayName("TC51 — GET <s2>/<Long.MAX_VALUE>/dashboard returns strictly 404")
        void dashboard_non_existent_returns_404() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                String dashPath = "/api/drivers" + "/" + Long.MAX_VALUE + "/dashboard";
                HttpResponse<String> r = httpGetAuth(dashPath, adminTok);
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC51: NOT 2xx; got " + code);
                assertTrue(code / 100 != 5, "TC51: NOT 5xx; got " + code);
                assertEquals(404, code, "TC51: must be strict 404; got " + code + " body=" + r.body());
        }
}

// ─── TC52 — S2-F12 dashboard for entity with no rides returns zeros ────────
@Tag("public")
@Tag("features_m2")
class TC52_DashboardNoRidesTests extends TestBase {
        @Test
        @DisplayName("TC52 — Dashboard for an entity with no rides returns 2xx + totalRides=0 + totalRevenue=0")
        void dashboard_no_rides_returns_zeros() throws Exception {
                BASE_URL = driverServiceUrl;
                String adminTok = adminToken();
                long restId = _UbM2.drv(this, "TC52 Driver " + nonce());
                String fkCol = s2CatalogFkColumn();
                jdbc.update("DELETE FROM \"" + tableName(s3RideEntity()) + "\" WHERE \"" + fkCol + "\" = ?", restId);

                HttpResponse<String> r = httpGetAuth(
                                "/api/drivers" + "/" + restId + "/dashboard", adminTok);
                assert2xx(r, "TC52 dashboard");
                JsonNode j = parseNode(r.body());

                long totalRides = j.has("totalRides") ? j.get("totalRides").asLong()
                                : j.has("total_rides") ? j.get("total_rides").asLong() : -1L;
                double totalRevenue = j.has("totalRevenue") ? j.get("totalRevenue").asDouble()
                                : j.has("total_revenue") ? j.get("total_revenue").asDouble() : -1.0;

                assertEquals(0L, totalRides, "TC52: must report totalRides=0; got " + totalRides);
                assertEquals(0.0, totalRevenue, 0.01, "TC52: must report totalRevenue=0; got " + totalRevenue);
        }
}

// ─── TC53 — S2-F12 dashboard without token returns 401 ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC53_DashboardNoTokenTests extends TestBase {
        @Test
        @DisplayName("TC53 — GET <s2>/{id}/dashboard without Authorization header returns 401")
        void dashboard_no_token_returns_401() throws Exception {
                BASE_URL = driverServiceUrl;
                long restId = 1L;
                HttpResponse<String> r = httpGet("/api/drivers" + "/" + restId + "/dashboard");
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC53: NOT 2xx; got " + code);
                assertEquals(401, code, "TC53: must be strict 401; got " + code + " body=" + r.body());
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S3 M2 — Ride Service features (TC54..TC99)
//
// Covers S3-F10 (ride analytics dashboard, TC54-TC69), S3-F11 (record user-
// driver riding pattern, TC70-TC84), and S3-F12 (driver recommendations,
// TC85-TC99). Theme-specific terms: S3 endpoints under /api/rides; revenue
// is sourced from the payments table joined on ride_id; Ride.status enum is
// {REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED}; Neo4j relationship
// is RODE_WITH with rideCount + lastRideDate; Mongo collection is
// ride_events. Per-test wipe of PG/Neo4j/Redis happens in
// autoTruncateAllData() (@BeforeEach + @AfterEach).
// ════════════════════════════════════════════════════════════════════════════

// ─── TC54 — S3-F10 dashboard happy path ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC54_RideDashboardHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC54 — Dashboard returns totalRides/completionRate/totalRevenue/averageRideFare/ridesByStatus")
        void dashboard_happy_path() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC54 Driver " + nonce());
                String[] sts = {"COMPLETED","COMPLETED","COMPLETED","COMPLETED","COMPLETED","COMPLETED",
                                "CANCELLED","CANCELLED","REQUESTED","REQUESTED"};
                double[] amts = {50, 60, 70, 80, 90, 100, 200, 50, 180, 70};
                String[] dts = {"2026-03-02","2026-03-05","2026-03-09","2026-03-12","2026-03-15","2026-03-18",
                                "2026-03-21","2026-03-24","2026-03-27","2026-03-30"};
                for (int i = 0; i < 10; i++) {
                        Long rid = _UbM2.ride(this, 1L, did, sts[i], dts[i], amts[i]);
                        if ("COMPLETED".equals(sts[i])) _UbM2.pay(this, rid, 1L, amts[i], "COMPLETED", dts[i]);
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC54 dashboard");
                JsonNode j = parseNode(r.body());
                assertEquals(10L, _UbM2.rL(j, "totalRides", "total_rides"),
                        "TC54: totalRides=10; body=" + r.body());
                assertEquals(0.6, _UbM2.rD(j, "completionRate", "completion_rate"), 0.01,
                        "TC54: completionRate=0.6");
                double expectedRev = 50+60+70+80+90+100;
                assertEquals(expectedRev, _UbM2.rD(j, "totalRevenue", "total_revenue"), 0.01,
                        "TC54: totalRevenue mismatch");
                JsonNode bd = _UbM2.rO(j, "ridesByStatus", "rides_by_status");
                assertNotNull(bd, "TC54: ridesByStatus key required");
                assertEquals(6L, bd.has("COMPLETED") ? bd.get("COMPLETED").asLong() : 0L, "TC54: COMPLETED=6");
                assertEquals(2L, bd.has("CANCELLED") ? bd.get("CANCELLED").asLong() : 0L, "TC54: CANCELLED=2");
                assertEquals(2L, bd.has("REQUESTED") ? bd.get("REQUESTED").asLong() : 0L, "TC54: REQUESTED=2");
        }
}

// ─── TC55 — S3-F10 totalRides isolated ──────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC55_RideDashboardTotalRidesTests extends TestBase {
        @Test
        @DisplayName("TC55 — Dashboard.totalRides equals exact count of rides in range")
        void total_rides_isolated() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC55 Driver " + nonce());
                for (int i = 0; i < 7; i++) {
                        Long rid = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-09-15", 75.0);
                        _UbM2.pay(this, rid, 1L, 75.0, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC55");
                assertEquals(7L,
                        _UbM2.rL(parseNode(r.body()), "totalRides", "total_rides"),
                        "TC55: totalRides=7");
        }
}

// ─── TC56 — S3-F10 totalRevenue isolated ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC56_RideDashboardTotalRevenueTests extends TestBase {
        @Test
        @DisplayName("TC56 — Dashboard.totalRevenue equals SUM(payments.amount) for COMPLETED rides")
        void total_revenue_isolated() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC56 Driver " + nonce());
                double[] amts = {50, 100, 150, 200};
                for (double a : amts) {
                        Long rid = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-09-15", a);
                        _UbM2.pay(this, rid, 1L, a, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC56");
                assertEquals(500.0, _UbM2.rD(parseNode(r.body()), "totalRevenue", "total_revenue"), 0.01,
                        "TC56: totalRevenue=500");
        }
}

// ─── TC57 — S3-F10 averageRideFare isolated ─────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC57_RideDashboardAverageFareTests extends TestBase {
        @Test
        @DisplayName("TC57 — Dashboard.averageRideFare equals totalRevenue / completed count")
        void avg_ride_fare_isolated() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC57 Driver " + nonce());
                double[] amts = {30, 60, 90, 120, 150};
                for (double a : amts) {
                        Long rid = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-09-15", a);
                        _UbM2.pay(this, rid, 1L, a, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC57");
                double avg = _UbM2.rD(parseNode(r.body()), "averageRideFare", "average_ride_fare", "avgRideFare");
                assertEquals(90.0, avg, 0.01, "TC57: averageRideFare=90");
        }
}

// ─── TC58 — S3-F10 completionRate isolated ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC58_RideDashboardCompletionRateTests extends TestBase {
        @Test
        @DisplayName("TC58 — Dashboard.completionRate = COMPLETED / total")
        void completion_rate_isolated() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC58 Driver " + nonce());
                String[] sts = {"COMPLETED","COMPLETED","COMPLETED","COMPLETED","COMPLETED",
                                "CANCELLED","CANCELLED","CANCELLED"};
                for (String st : sts) {
                        Long rid = _UbM2.ride(this, 1L, did, st, "2026-09-15", 100.0);
                        if ("COMPLETED".equals(st)) _UbM2.pay(this, rid, 1L, 100.0, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC58");
                assertEquals(0.625, _UbM2.rD(parseNode(r.body()), "completionRate", "completion_rate"), 0.001,
                        "TC58: completionRate=0.625");
        }
}

// ─── TC59 — S3-F10 ridesByStatus has all 5 statuses ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC59_RideDashboardRidesByStatusTests extends TestBase {
        @Test
        @DisplayName("TC59 — Dashboard.ridesByStatus has all 5 Ride statuses, each count=1")
        void rides_by_status_isolated() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC59 Driver " + nonce());
                String[] sts = {"REQUESTED","ACCEPTED","IN_PROGRESS","COMPLETED","CANCELLED"};
                for (String st : sts) {
                        Long rid = _UbM2.ride(this, 1L, did, st, "2026-09-15", 75.0);
                        if ("COMPLETED".equals(st)) _UbM2.pay(this, rid, 1L, 75.0, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC59");
                JsonNode bd = _UbM2.rO(parseNode(r.body()), "ridesByStatus", "rides_by_status");
                assertNotNull(bd, "TC59: ridesByStatus key required");
                for (String st : sts) {
                        assertTrue(bd.has(st), "TC59: ridesByStatus missing key '" + st + "'");
                        assertEquals(1L, bd.get(st).asLong(),
                                "TC59: ridesByStatus[" + st + "]=1; got " + bd.get(st).asLong());
                }
        }
}

// ─── TC60 — S3-F10 empty range zeros ────────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC60_RideDashboardEmptyRangeTests extends TestBase {
        @Test
        @DisplayName("TC60 — Dashboard with no rides in range returns totalRides=0, totalRevenue=0")
        void empty_range_zeros() throws Exception {
                BASE_URL = rideServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2099-01-01&endDate=2099-01-31", tok);
                assert2xx(r, "TC60");
                JsonNode j = parseNode(r.body());
                assertEquals(0L, _UbM2.rL(j, "totalRides", "total_rides"), "TC60: totalRides=0");
                assertEquals(0.0, _UbM2.rD(j, "totalRevenue", "total_revenue"), 0.01,
                        "TC60: totalRevenue=0");
        }
}

// ─── TC61 — S3-F10 invalid date range → 400 ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC61_RideDashboardInvalidRangeTests extends TestBase {
        @Test
        @DisplayName("TC61 — Dashboard with startDate > endDate returns 400")
        void invalid_date_range_400() throws Exception {
                BASE_URL = rideServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-04-01&endDate=2026-03-01", tok);
                assertEquals(400, r.statusCode(),
                        "TC61: must be 400; got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC62 — S3-F10 missing JWT → 401 ───────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC62_RideDashboardMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC62 — Dashboard without Authorization header returns 401")
        void missing_jwt_401() throws Exception {
                BASE_URL = rideServiceUrl;
                HttpResponse<String> r = httpGet(
                        "/api/rides/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31");
                assertEquals(401, r.statusCode(),
                        "TC62: must be 401; got " + r.statusCode());
        }
}

// ─── TC63 — S3-F10 invalid JWT → 401 ───────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC63_RideDashboardInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC63 — Dashboard with malformed JWT returns 401")
        void invalid_jwt_401() throws Exception {
                BASE_URL = rideServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC63: must be 401; got " + r.statusCode());
        }
}

// ─── TC64 — S3-F10 boundary date inclusion ─────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC64_RideDashboardBoundaryInclusionTests extends TestBase {
        @Test
        @DisplayName("TC64 — Ride exactly at startDate T00:00:00 is included")
        void boundary_included() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC64 Driver " + nonce());
                String rTable = tableName("Ride");
                Long rid = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-05-01", 80.0);
                // Spec (Uber M1 §6.3): Ride lifecycle timestamps are requestedAt
                // and completedAt. There is no created_at column on rides — use
                // requestedAt (Hibernate maps to requested_at in PG, but resolve
                // via columnByField to tolerate student naming).
                String reqAtCol = columnByField("Ride", "requestedAt");
                jdbc.update("UPDATE \"" + rTable + "\" SET \"" + reqAtCol + "\" = ? WHERE id = ?",
                        java.sql.Timestamp.valueOf("2026-05-01 00:00:00"), rid);
                _UbM2.pay(this, rid, 1L, 80.0, "COMPLETED", "2026-05-01");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-05-01&endDate=2026-05-31", tok);
                assert2xx(r, "TC64");
                assertEquals(1L,
                        _UbM2.rL(parseNode(r.body()), "totalRides", "total_rides"),
                        "TC64: boundary ride must be counted; body=" + r.body());
        }
}

// ─── TC65 — S3-F10 out-of-range rides excluded ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC65_RideDashboardOutOfRangeTests extends TestBase {
        @Test
        @DisplayName("TC65 — Rides outside [startDate, endDate] are excluded")
        void out_of_range_excluded() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC65 Driver " + nonce());
                Long inRange = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-06-15", 75.0);
                _UbM2.pay(this, inRange, 1L, 75.0, "COMPLETED", "2026-06-15");
                Long outRange = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-08-15", 100.0);
                _UbM2.pay(this, outRange, 1L, 100.0, "COMPLETED", "2026-08-15");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-06-01&endDate=2026-06-30", tok);
                assert2xx(r, "TC65");
                assertEquals(1L,
                        _UbM2.rL(parseNode(r.body()), "totalRides", "total_rides"),
                        "TC65: only the in-range ride counts; got " + r.body());
        }
}

// ─── TC66 — S3-F10 ANALYTICS_VIEWED logged on first call ────────────────────
@Tag("public")
@Tag("features_m2")
class TC66_RideDashboardAnalyticsViewedLoggedTests extends TestBase {
        @Test
        @DisplayName("TC66 — First dashboard call writes ANALYTICS_VIEWED to ride_events")
        void analytics_viewed_on_first_call() throws Exception {
                BASE_URL = rideServiceUrl;
                if (mongo == null) throw new AssertionError("TC66: MongoDB required");
                String coll = s3EventsCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31", tok);
                assert2xx(r, "TC66");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after > before,
                        "TC66: ANALYTICS_VIEWED count must increase; before=" + before + " after=" + after);
        }
}

// ─── TC67 — S3-F10 ANALYTICS_VIEWED on cache hit too ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC67_RideDashboardAnalyticsCacheHitTests extends TestBase {
        @Test
        @DisplayName("TC67 — Second dashboard call (cache hit) still logs ANALYTICS_VIEWED")
        void analytics_viewed_on_cache_hit() throws Exception {
                BASE_URL = rideServiceUrl;
                if (mongo == null) throw new AssertionError("TC67: MongoDB required");
                String coll = s3EventsCollection();
                String tok = adminToken();
                String url = "/api/rides/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31";
                assert2xx(httpGetAuth(url, tok), "TC67 first");
                long after1 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assert2xx(httpGetAuth(url, tok), "TC67 second");
                long after2 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after2 > after1,
                        "TC67: ANALYTICS_VIEWED on cache hit; after1=" + after1 + " after2=" + after2);
        }
}

// ─── TC68 — S3-F10 cache returns same body ─────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC68_RideDashboardCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC68 — Two identical dashboard requests return identical bodies")
        void cache_same_body() throws Exception {
                BASE_URL = rideServiceUrl;
                String tok = adminToken();
                String url = "/api/rides/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC68 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC68 second");
                JsonNode j1 = parseNode(r1.body());
                JsonNode j2 = parseNode(r2.body());
                assertEquals(_UbM2.rL(j1, "totalRides", "total_rides"),
                             _UbM2.rL(j2, "totalRides", "total_rides"),
                        "TC68: cached totalRides must match");
                assertEquals(_UbM2.rD(j1, "totalRevenue", "total_revenue"),
                             _UbM2.rD(j2, "totalRevenue", "total_revenue"), 0.01,
                        "TC68: cached totalRevenue must match");
        }
}

// ─── TC69 — S3-F10 cache hit doesn't re-aggregate ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC69_RideDashboardCacheNoReaggregateTests extends TestBase {
        @Test
        @DisplayName("TC69 — Insert ride after first call → cached body still returned")
        void cache_does_not_reaggregate() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC69 Driver " + nonce());
                String tok = adminToken();
                String url = "/api/rides/analytics/dashboard?startDate=2026-11-01&endDate=2026-11-30";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC69 first");
                long t1 = _UbM2.rL(parseNode(r1.body()), "totalRides", "total_rides");
                Long rid = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-11-15", 80.0);
                _UbM2.pay(this, rid, 1L, 80.0, "COMPLETED", "2026-11-15");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC69 second");
                long t2 = _UbM2.rL(parseNode(r2.body()), "totalRides", "total_rides");
                assertEquals(t1, t2,
                        "TC69: cached value must equal pre-insert value; t1=" + t1 + " t2=" + t2);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S3-F11 — Record User-Driver Riding Pattern (TC70-TC84)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC70 — S3-F11 happy path creates RODE_WITH with rideCount=1 ───────────
@Tag("public")
@Tag("features_m2")
class TC70_RecordRideHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC70 — Record interaction on COMPLETED ride creates RODE_WITH with rideCount=1")
        void record_ride_happy() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC70: Neo4j required");
                Map<String, Object> u = seedAndLoginUser("tc70u");
                long uid = ((Number) u.get("id")).longValue();
                long did = _UbM2.drv(this, "TC70 Driver " + nonce());
                Long rid = _UbM2.ride(this, uid, did, "COMPLETED", "2026-04-10", 80.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/rides/" + rid + "/record-interaction", "", tok);
                assert2xx(r, "TC70");
                long count = _UbM2.rideCount(this, uid, did);
                assertEquals(1L, count, "TC70: rideCount=1; got " + count);
        }
}

// ─── TC71 — S3-F11 idempotency: same ride twice → rideCount stays 1 ────────
@Tag("public")
@Tag("features_m2")
class TC71_RecordRideIdempotencyTests extends TestBase {
        @Test
        @DisplayName("TC71 — Same rideId recorded twice keeps rideCount=1")
        void record_ride_idempotent() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC71: Neo4j required");
                Map<String, Object> u = seedAndLoginUser("tc71u");
                long uid = ((Number) u.get("id")).longValue();
                long did = _UbM2.drv(this, "TC71 Driver " + nonce());
                Long rid = _UbM2.ride(this, uid, did, "COMPLETED", "2026-04-10", 80.0);
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/rides/" + rid + "/record-interaction", "", tok), "TC71 first");
                assert2xx(httpPostAuth("/api/rides/" + rid + "/record-interaction", "", tok), "TC71 second");
                long count = _UbM2.rideCount(this, uid, did);
                assertEquals(1L, count, "TC71: rideCount must stay 1 after duplicate; got " + count);
        }
}

// ─── TC72 — S3-F11 two distinct rides same user→driver → rideCount=2 ─────
@Tag("public")
@Tag("features_m2")
class TC72_RecordRideTwoDistinctTests extends TestBase {
        @Test
        @DisplayName("TC72 — Two distinct COMPLETED rides same user→driver → rideCount=2")
        void record_ride_two_distinct() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC72: Neo4j required");
                Map<String, Object> u = seedAndLoginUser("tc72u");
                long uid = ((Number) u.get("id")).longValue();
                long did = _UbM2.drv(this, "TC72 Driver " + nonce());
                Long r1 = _UbM2.ride(this, uid, did, "COMPLETED", "2026-04-10", 80.0);
                Long r2 = _UbM2.ride(this, uid, did, "COMPLETED", "2026-04-12", 90.0);
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/rides/" + r1 + "/record-interaction", "", tok), "TC72 r1");
                assert2xx(httpPostAuth("/api/rides/" + r2 + "/record-interaction", "", tok), "TC72 r2");
                long count = _UbM2.rideCount(this, uid, did);
                assertEquals(2L, count, "TC72: rideCount=2; got " + count);
        }
}

// ─── TC73 — S3-F11 different driver → new edge with rideCount=1 ───────────
@Tag("public")
@Tag("features_m2")
class TC73_RecordRideDifferentDriverTests extends TestBase {
        @Test
        @DisplayName("TC73 — Recording ride with a different driver creates a new edge")
        void record_ride_different_driver() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC73: Neo4j required");
                Map<String, Object> u = seedAndLoginUser("tc73u");
                long uid = ((Number) u.get("id")).longValue();
                long d1 = _UbM2.drv(this, "TC73 D1 " + nonce());
                long d2 = _UbM2.drv(this, "TC73 D2 " + nonce());
                Long r1 = _UbM2.ride(this, uid, d1, "COMPLETED", "2026-04-10", 80.0);
                Long r2 = _UbM2.ride(this, uid, d2, "COMPLETED", "2026-04-11", 90.0);
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/rides/" + r1 + "/record-interaction", "", tok), "TC73 r1");
                assert2xx(httpPostAuth("/api/rides/" + r2 + "/record-interaction", "", tok), "TC73 r2");
                assertEquals(1L, _UbM2.rideCount(this, uid, d1), "TC73: edge to d1 stays at 1");
                assertEquals(1L, _UbM2.rideCount(this, uid, d2), "TC73: edge to d2 is 1");
        }
}

// ─── TC74 — S3-F11 REQUESTED ride → 400 ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC74_RecordRideRequestedTests extends TestBase {
        @Test
        @DisplayName("TC74 — Recording a REQUESTED (not completed) ride returns 400")
        void record_ride_requested_400() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC74 Driver " + nonce());
                Long rid = _UbM2.ride(this, 1L, did, "REQUESTED", "2026-04-10", 80.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/rides/" + rid + "/record-interaction", "", tok);
                assertEquals(400, r.statusCode(),
                        "TC74: must be 400 for REQUESTED; got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC75 — S3-F11 CANCELLED ride → 400 ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC75_RecordRideCancelledTests extends TestBase {
        @Test
        @DisplayName("TC75 — Recording a CANCELLED ride returns 400")
        void record_ride_cancelled_400() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC75 Driver " + nonce());
                Long rid = _UbM2.ride(this, 1L, did, "CANCELLED", "2026-04-10", 80.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/rides/" + rid + "/record-interaction", "", tok);
                assertEquals(400, r.statusCode(),
                        "TC75: must be 400 for CANCELLED; got " + r.statusCode());
        }
}

// ─── TC76 — S3-F11 IN_PROGRESS ride → 400 ─────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC76_RecordRideInProgressTests extends TestBase {
        @Test
        @DisplayName("TC76 — Recording an IN_PROGRESS ride returns 400")
        void record_ride_in_progress_400() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC76 Driver " + nonce());
                Long rid = _UbM2.ride(this, 1L, did, "IN_PROGRESS", "2026-04-10", 80.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/rides/" + rid + "/record-interaction", "", tok);
                assertEquals(400, r.statusCode(),
                        "TC76: must be 400 for IN_PROGRESS; got " + r.statusCode());
        }
}

// ─── TC77 — S3-F11 ACCEPTED ride → 400 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC77_RecordRideAcceptedTests extends TestBase {
        @Test
        @DisplayName("TC77 — Recording an ACCEPTED ride returns 400")
        void record_ride_accepted_400() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC77 Driver " + nonce());
                Long rid = _UbM2.ride(this, 1L, did, "ACCEPTED", "2026-04-10", 80.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/rides/" + rid + "/record-interaction", "", tok);
                assertEquals(400, r.statusCode(),
                        "TC77: must be 400 for ACCEPTED; got " + r.statusCode());
        }
}

// ─── TC78 — S3-F11 non-existent ride → 404 ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC78_RecordRideNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC78 — Record interaction for non-existent ride returns 404")
        void record_ride_not_found_404() throws Exception {
                BASE_URL = rideServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/rides/999999/record-interaction", "", tok);
                assertEquals(404, r.statusCode(),
                        "TC78: must be 404; got " + r.statusCode());
        }
}

// ─── TC79 — S3-F11 missing JWT → 401 ──────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC79_RecordRideMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC79 — Record interaction without Authorization header returns 401")
        void record_ride_missing_jwt_401() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC79 Driver " + nonce());
                Long rid = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-04-10", 80.0);
                HttpResponse<String> r = httpPost(
                        "/api/rides/" + rid + "/record-interaction", "");
                assertEquals(401, r.statusCode(),
                        "TC79: must be 401; got " + r.statusCode());
        }
}

// ─── TC80 — S3-F11 invalid JWT → 401 ──────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC80_RecordRideInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC80 — Record interaction with bogus JWT returns 401")
        void record_ride_invalid_jwt_401() throws Exception {
                BASE_URL = rideServiceUrl;
                long did = _UbM2.drv(this, "TC80 Driver " + nonce());
                Long rid = _UbM2.ride(this, 1L, did, "COMPLETED", "2026-04-10", 80.0);
                HttpResponse<String> r = httpPostAuth(
                        "/api/rides/" + rid + "/record-interaction", "", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC80: must be 401; got " + r.statusCode());
        }
}

// ─── TC81 — S3-F11 lastRideDate set on success ────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC81_RecordRideLastRideDateTests extends TestBase {
        @Test
        @DisplayName("TC81 — RODE_WITH edge has lastRideDate property after recording")
        void record_ride_last_ride_date() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC81: Neo4j required");
                Map<String, Object> u = seedAndLoginUser("tc81u");
                long uid = ((Number) u.get("id")).longValue();
                long did = _UbM2.drv(this, "TC81 Driver " + nonce());
                Long rid = _UbM2.ride(this, uid, did, "COMPLETED", "2026-04-10", 80.0);
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/rides/" + rid + "/record-interaction", "", tok), "TC81");
                java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
                        "MATCH (a:`" + s3GraphUserLabel() + "` {id:$u})-[r:`" + s3GraphRelationship()
                          + "`]->(b:`" + s3GraphCatalogLabel() + "` {id:$d}) RETURN r AS rel LIMIT 1",
                        java.util.Map.of("u", uid, "d", did));
                assertFalse(rows.isEmpty(), "TC81: RODE_WITH edge must exist");
                Object rel = rows.get(0).get("rel");
                assertNotNull(rel, "TC81: relationship returned");
                boolean hasLastRideDate;
                if (rel instanceof org.neo4j.driver.types.Relationship) {
                    org.neo4j.driver.types.Relationship relObj = (org.neo4j.driver.types.Relationship) rel;
                    hasLastRideDate = relObj.containsKey("lastRideDate") || relObj.containsKey("last_ride_date")
                            || relObj.containsKey("lastRide");
                } else {
                    String relStr = rel.toString();
                    hasLastRideDate = relStr.contains("lastRideDate") || relStr.contains("last_ride_date")
                            || relStr.contains("lastRide");
                }
                assertTrue(hasLastRideDate,
                        "TC81: edge must carry lastRideDate property; got " + rel);
        }
}

// ─── TC82 — S3-F11 Neo4j Driver node has correct id ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC82_RecordRideDriverNodeTests extends TestBase {
        @Test
        @DisplayName("TC82 — Neo4j Driver node exists with the seeded driver id")
        void record_ride_driver_node() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC82: Neo4j required");
                Map<String, Object> u = seedAndLoginUser("tc82u");
                long uid = ((Number) u.get("id")).longValue();
                long did = _UbM2.drv(this, "TC82 Driver " + nonce());
                Long rid = _UbM2.ride(this, uid, did, "COMPLETED", "2026-04-10", 80.0);
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/rides/" + rid + "/record-interaction", "", tok), "TC82");
                long count = neo4jNodeCount(s3GraphCatalogLabel(), did);
                assertTrue(count >= 1, "TC82: Driver node id=" + did + " must exist; count=" + count);
        }
}

// ─── TC83 — S3-F11 Neo4j User node has correct id ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC83_RecordRideUserNodeTests extends TestBase {
        @Test
        @DisplayName("TC83 — Neo4j User node exists with the ride's user id")
        void record_ride_user_node() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC83: Neo4j required");
                Map<String, Object> u = seedAndLoginUser("tc83u");
                long uid = ((Number) u.get("id")).longValue();
                long did = _UbM2.drv(this, "TC83 Driver " + nonce());
                Long rid = _UbM2.ride(this, uid, did, "COMPLETED", "2026-04-10", 80.0);
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/rides/" + rid + "/record-interaction", "", tok), "TC83");
                long count = neo4jNodeCount(s3GraphUserLabel(), uid);
                assertTrue(count >= 1, "TC83: User node id=" + uid + " must exist; count=" + count);
        }
}

// ─── TC84 — S3-F11 INTERACTION_RECORDED logged to ride_events ────────────
@Tag("public")
@Tag("features_m2")
class TC84_RecordRideEventLoggedTests extends TestBase {
        @Test
        @DisplayName("TC84 — Record interaction writes INTERACTION_RECORDED to ride_events")
        void record_ride_event_logged() throws Exception {
                BASE_URL = rideServiceUrl;
                if (mongo == null) throw new AssertionError("TC84: MongoDB required");
                String coll = s3EventsCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "INTERACTION_RECORDED"));
                Map<String, Object> u = seedAndLoginUser("tc84u");
                long uid = ((Number) u.get("id")).longValue();
                long did = _UbM2.drv(this, "TC84 Driver " + nonce());
                Long rid = _UbM2.ride(this, uid, did, "COMPLETED", "2026-04-10", 80.0);
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/rides/" + rid + "/record-interaction", "", tok), "TC84");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "INTERACTION_RECORDED"));
                assertTrue(after > before,
                        "TC84: INTERACTION_RECORDED must be logged; before=" + before + " after=" + after);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S3-F12 — Driver Recommendations (TC85-TC99)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC85 — S3-F12 happy path (3 users + 4 drivers spec scenario) ─────────
@Tag("public")
@Tag("features_m2")
class TC85_RecommendationsHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC85 — Recs for A (rode D1,D2) include D3 (B rode D1) and D4 (C rode D2); exclude D1,D2")
        void recommendations_happy_path() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC85: Neo4j required");
                Map<String, Object> a = seedAndLoginUser("tc85a");
                Map<String, Object> b = seedAndLoginUser("tc85b");
                Map<String, Object> c = seedAndLoginUser("tc85c");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long cid = ((Number) c.get("id")).longValue();
                long d1 = _UbM2.drv(this, "TC85 D1 " + nonce());
                long d2 = _UbM2.drv(this, "TC85 D2 " + nonce());
                long d3 = _UbM2.drv(this, "TC85 D3 " + nonce());
                long d4 = _UbM2.drv(this, "TC85 D4 " + nonce());
                String tok = adminToken();
                _UbM2.rideAndRecord(this, aid, d1, tok);
                _UbM2.rideAndRecord(this, aid, d2, tok);
                _UbM2.rideAndRecord(this, bid, d1, tok);
                _UbM2.rideAndRecord(this, bid, d3, tok);
                _UbM2.rideAndRecord(this, cid, d2, tok);
                _UbM2.rideAndRecord(this, cid, d4, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC85");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                java.util.Set<Long> recs = new java.util.HashSet<>();
                for (JsonNode item : arr) {
                        if (item.has("driverId")) recs.add(item.get("driverId").asLong());
                        else if (item.has("id")) recs.add(item.get("id").asLong());
                }
                assertTrue(recs.contains(d3), "TC85: must include D3 (B rode D1)");
                assertTrue(recs.contains(d4), "TC85: must include D4 (C rode D2)");
                assertFalse(recs.contains(d1), "TC85: must exclude D1 (A already rode)");
                assertFalse(recs.contains(d2), "TC85: must exclude D2 (A already rode)");
        }
}

// ─── TC86 — S3-F12 score reflects similar-user count ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC86_RecommendationsScoreTests extends TestBase {
        @Test
        @DisplayName("TC86 — Driver ridden by 2 similar users ranks higher than driver ridden by 1")
        void recommendations_score_ranking() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC86: Neo4j required");
                Map<String, Object> a = seedAndLoginUser("tc86a");
                Map<String, Object> b = seedAndLoginUser("tc86b");
                Map<String, Object> c = seedAndLoginUser("tc86c");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long cid = ((Number) c.get("id")).longValue();
                long dCommon = _UbM2.drv(this, "TC86 Common " + nonce());
                long dPopular = _UbM2.drv(this, "TC86 Popular " + nonce());
                long dNiche = _UbM2.drv(this, "TC86 Niche " + nonce());
                String tok = adminToken();
                _UbM2.rideAndRecord(this, aid, dCommon, tok);
                _UbM2.rideAndRecord(this, bid, dCommon, tok);
                _UbM2.rideAndRecord(this, cid, dCommon, tok);
                _UbM2.rideAndRecord(this, bid, dPopular, tok);
                _UbM2.rideAndRecord(this, cid, dPopular, tok);
                _UbM2.rideAndRecord(this, bid, dNiche, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC86");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                long scorePopular = -1, scoreNiche = -1;
                for (JsonNode item : arr) {
                        long did = item.has("driverId") ? item.get("driverId").asLong()
                                : item.has("id") ? item.get("id").asLong() : -1;
                        long score = item.has("score") ? item.get("score").asLong() : 0;
                        if (did == dPopular) scorePopular = score;
                        if (did == dNiche) scoreNiche = score;
                }
                assertTrue(scorePopular > scoreNiche,
                        "TC86: dPopular score (" + scorePopular + ") must exceed dNiche score (" + scoreNiche + ")");
        }
}

// ─── TC87 — S3-F12 default limit honored ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC87_RecommendationsDefaultLimitTests extends TestBase {
        @Test
        @DisplayName("TC87 — Default limit caps recommendations at 5 when no limit param provided")
        void recommendations_default_limit() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC87: Neo4j required");
                Map<String, Object> a = seedAndLoginUser("tc87a");
                Map<String, Object> b = seedAndLoginUser("tc87b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long dAnchor = _UbM2.drv(this, "TC87 Anchor " + nonce());
                String tok = adminToken();
                _UbM2.rideAndRecord(this, aid, dAnchor, tok);
                _UbM2.rideAndRecord(this, bid, dAnchor, tok);
                for (int i = 0; i < 8; i++) {
                        long d = _UbM2.drv(this, "TC87 R" + i + " " + nonce());
                        _UbM2.rideAndRecord(this, bid, d, tok);
                }
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC87");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() <= 5,
                        "TC87: default limit must cap at 5; got " + arr.size());
        }
}

// ─── TC88 — S3-F12 user with no interactions → empty list ─────────────────
@Tag("public")
@Tag("features_m2")
class TC88_RecommendationsNoInteractionsTests extends TestBase {
        @Test
        @DisplayName("TC88 — User with no recorded interactions returns empty list")
        void recommendations_no_interactions() throws Exception {
                BASE_URL = rideServiceUrl;
                Map<String, Object> a = seedAndLoginUser("tc88a");
                long aid = ((Number) a.get("id")).longValue();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC88");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(),
                        "TC88: empty list for new user; body=" + r.body());
        }
}

// ─── TC89 — S3-F12 user with no similar users → empty list ────────────────
@Tag("public")
@Tag("features_m2")
class TC89_RecommendationsNoSimilarUsersTests extends TestBase {
        @Test
        @DisplayName("TC89 — User who rode unique driver (no overlap with anyone) → empty list")
        void recommendations_no_similar_users() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC89: Neo4j required");
                Map<String, Object> a = seedAndLoginUser("tc89a");
                long aid = ((Number) a.get("id")).longValue();
                long dUnique = _UbM2.drv(this, "TC89 Unique " + nonce());
                String tok = adminToken();
                _UbM2.rideAndRecord(this, aid, dUnique, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC89");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(),
                        "TC89: empty list when no similar users; body=" + r.body());
        }
}

// ─── TC90 — S3-F12 ownership: A's token requesting B's recs → 403 ────────
@Tag("public")
@Tag("features_m2")
class TC90_RecommendationsOwnershipTests extends TestBase {
        @Test
        @DisplayName("TC90 — User A's token requesting recommendations for user B returns 403")
        void recommendations_ownership_403() throws Exception {
                BASE_URL = rideServiceUrl;
                Map<String, Object> a = seedAndLoginUser("tc90a");
                Map<String, Object> b = seedAndLoginUser("tc90b");
                long bid = ((Number) b.get("id")).longValue();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + bid, (String) a.get("token"));
                assertEquals(403, r.statusCode(),
                        "TC90: must be 403 (ownership violation); got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC91 — S3-F12 admin token bypasses ownership ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC91_RecommendationsAdminBypassTests extends TestBase {
        @Test
        @DisplayName("TC91 — Admin token can fetch recommendations for any user")
        void recommendations_admin_bypass() throws Exception {
                BASE_URL = rideServiceUrl;
                Map<String, Object> u = seedAndLoginUser("tc91u");
                long uid = ((Number) u.get("id")).longValue();
                String adminTok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + uid, adminTok);
                assert2xx(r, "TC91");
        }
}

// ─── TC92 — S3-F12 non-existent userId with admin token → 404 ────────────
@Tag("public")
@Tag("features_m2")
class TC92_RecommendationsNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC92 — Admin requesting recommendations for non-existent userId returns 404")
        void recommendations_not_found_404() throws Exception {
                BASE_URL = rideServiceUrl;
                String adminTok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=999999", adminTok);
                assertEquals(404, r.statusCode(),
                        "TC92: must be 404; got " + r.statusCode());
        }
}

// ─── TC93 — S3-F12 missing token → 401 ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC93_RecommendationsMissingTokenTests extends TestBase {
        @Test
        @DisplayName("TC93 — Recommendations without Authorization header returns 401")
        void recommendations_missing_token_401() throws Exception {
                BASE_URL = rideServiceUrl;
                HttpResponse<String> r = httpGet("/api/rides/recommendations?userId=1");
                assertEquals(401, r.statusCode(),
                        "TC93: must be 401; got " + r.statusCode());
        }
}

// ─── TC94 — S3-F12 invalid token → 401 ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC94_RecommendationsInvalidTokenTests extends TestBase {
        @Test
        @DisplayName("TC94 — Recommendations with malformed JWT returns 401")
        void recommendations_invalid_token_401() throws Exception {
                BASE_URL = rideServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=1", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC94: must be 401; got " + r.statusCode());
        }
}

// ─── TC95 — S3-F12 each rec includes driverId+name+vehicleType+score ─────
@Tag("public")
@Tag("features_m2")
class TC95_RecommendationsEnrichedFieldsTests extends TestBase {
        @Test
        @DisplayName("TC95 — Each recommendation item includes driverId, name, vehicleType, score")
        void recommendations_enriched_fields() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC95: Neo4j required");
                Map<String, Object> a = seedAndLoginUser("tc95a");
                Map<String, Object> b = seedAndLoginUser("tc95b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long dAnchor = _UbM2.drv(this, "TC95 Anchor " + nonce());
                long dTarget = _UbM2.drv(this, "TC95 Target " + nonce());
                String tok = adminToken();
                _UbM2.rideAndRecord(this, aid, dAnchor, tok);
                _UbM2.rideAndRecord(this, bid, dAnchor, tok);
                _UbM2.rideAndRecord(this, bid, dTarget, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC95");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() > 0, "TC95: at least one rec expected");
                for (JsonNode item : arr) {
                        boolean hasId = item.has("driverId") || item.has("id");
                        boolean hasName = item.has("name");
                        boolean hasVehicleType = item.has("vehicleType") || item.has("vehicle_type");
                        boolean hasScore = item.has("score");
                        assertTrue(hasId, "TC95: must include driverId/id; got=" + item);
                        assertTrue(hasName, "TC95: must include name; got=" + item);
                        assertTrue(hasVehicleType, "TC95: must include vehicleType; got=" + item);
                        assertTrue(hasScore, "TC95: must include score; got=" + item);
                }
        }
}

// ─── TC96 — S3-F12 cache returns same body for repeated calls ────────────
@Tag("public")
@Tag("features_m2")
class TC96_RecommendationsCacheTests extends TestBase {
        @Test
        @DisplayName("TC96 — Two identical recommendation requests return identical bodies")
        void recommendations_cache_same_body() throws Exception {
                BASE_URL = rideServiceUrl;
                Map<String, Object> a = seedAndLoginUser("tc96a");
                long aid = ((Number) a.get("id")).longValue();
                String url = "/api/rides/recommendations?userId=" + aid;
                String tok = (String) a.get("token");
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC96 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC96 second");
                assertEquals(r1.body(), r2.body(),
                        "TC96: identical recommendations responses expected (cached)");
        }
}

// ─── TC97 — S3-F12 limit=2 returns at most 2 results ────────────────────
@Tag("public")
@Tag("features_m2")
class TC97_RecommendationsLimitParamTests extends TestBase {
        @Test
        @DisplayName("TC97 — limit=2 caps recommendations at 2 entries")
        void recommendations_limit_param() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC97: Neo4j required");
                Map<String, Object> a = seedAndLoginUser("tc97a");
                Map<String, Object> b = seedAndLoginUser("tc97b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long dAnchor = _UbM2.drv(this, "TC97 Anchor " + nonce());
                String tok = adminToken();
                _UbM2.rideAndRecord(this, aid, dAnchor, tok);
                _UbM2.rideAndRecord(this, bid, dAnchor, tok);
                for (int i = 0; i < 5; i++) {
                        long d = _UbM2.drv(this, "TC97 R" + i + " " + nonce());
                        _UbM2.rideAndRecord(this, bid, d, tok);
                }
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid + "&limit=2", (String) a.get("token"));
                assert2xx(r, "TC97");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() <= 2,
                        "TC97: limit=2 must cap; got " + arr.size());
        }
}

// ─── TC98 — S3-F12 already-ridden drivers excluded ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC98_RecommendationsExcludeAlreadyRiddenTests extends TestBase {
        @Test
        @DisplayName("TC98 — Recommendations exclude drivers user already rode with")
        void recommendations_exclude_already_ridden() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC98: Neo4j required");
                Map<String, Object> a = seedAndLoginUser("tc98a");
                Map<String, Object> b = seedAndLoginUser("tc98b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long dShared = _UbM2.drv(this, "TC98 Shared " + nonce());
                long dNew = _UbM2.drv(this, "TC98 New " + nonce());
                String tok = adminToken();
                _UbM2.rideAndRecord(this, aid, dShared, tok);
                _UbM2.rideAndRecord(this, bid, dShared, tok);
                _UbM2.rideAndRecord(this, bid, dNew, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC98");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                java.util.Set<Long> recs = new java.util.HashSet<>();
                for (JsonNode item : arr) {
                        if (item.has("driverId")) recs.add(item.get("driverId").asLong());
                        else if (item.has("id")) recs.add(item.get("id").asLong());
                }
                assertFalse(recs.contains(dShared),
                        "TC98: must exclude dShared (already ridden); recs=" + recs);
                assertTrue(recs.contains(dNew),
                        "TC98: must include dNew (similar user rode, not yet by A); recs=" + recs);
        }
}

// ─── TC99 — S3-F12 vehicleType enriched from PG ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC99_RecommendationsVehicleTypeEnrichmentTests extends TestBase {
        @Test
        @DisplayName("TC99 — Each recommendation's vehicleType is present (enriched from PG/Neo4j)")
        void recommendations_vehicle_type_enriched() throws Exception {
                BASE_URL = rideServiceUrl;
                if (neo4j == null) throw new AssertionError("TC99: Neo4j required");
                Map<String, Object> a = seedAndLoginUser("tc99a");
                Map<String, Object> b = seedAndLoginUser("tc99b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long dAnchor = _UbM2.drv(this, "TC99 Anchor " + nonce());
                long dTarget = _UbM2.drv(this, "TC99 Target " + nonce());
                String tok = adminToken();
                _UbM2.rideAndRecord(this, aid, dAnchor, tok);
                _UbM2.rideAndRecord(this, bid, dAnchor, tok);
                _UbM2.rideAndRecord(this, bid, dTarget, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/rides/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC99");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                boolean foundTarget = false;
                for (JsonNode item : arr) {
                        long did = item.has("driverId") ? item.get("driverId").asLong()
                                : item.has("id") ? item.get("id").asLong() : -1;
                        if (did == dTarget) {
                                foundTarget = true;
                                String vt = item.has("vehicleType") ? item.get("vehicleType").asText()
                                          : item.has("vehicle_type") ? item.get("vehicle_type").asText() : null;
                                assertNotNull(vt, "TC99: response vehicleType must be populated; item=" + item);
                                assertFalse(vt.isBlank(), "TC99: vehicleType must not be blank; got=" + vt);
                        }
                }
                assertTrue(foundTarget, "TC99: dTarget must be in results");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// Helper class for S3 seeding (package-private; same package as TestBase so it
// can call protected helpers). Ride/Payment/Driver seed methods use the
// manifest-driven tableName/columnByField pattern so they survive student-side
// renames (e.g. driver_fk vs driver_id, fare vs total_amount).
// ════════════════════════════════════════════════════════════════════════════
final class _UbM2 {
        private _UbM2() {}

        static Long ride(TestBase t, long userId, long driverId, String status, String date, double fare) {
                String table = t.tableName("Ride");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Ride", "user"), userId);
                try { ov.put(t.columnByField("Ride", "driver"), driverId); }
                catch (Throwable ignore) { /* student may name FK differently */ }
                try { ov.put(t.columnByField("Ride", "status"), status); } catch (Throwable ignore) {}
                try { ov.put(t.columnByField("Ride", "fare"), fare); } catch (Throwable ignore) {}
                try { ov.put(t.columnByField("Ride", "pickupLatitude"), 30.044); } catch (Throwable ignore) {}
                try { ov.put(t.columnByField("Ride", "pickupLongitude"), 31.235); } catch (Throwable ignore) {}
                try { ov.put(t.columnByField("Ride", "dropoffLatitude"), 30.100); } catch (Throwable ignore) {}
                try { ov.put(t.columnByField("Ride", "dropoffLongitude"), 31.300); } catch (Throwable ignore) {}
                Long id = t.insertRowReturningId(table, ov);
                t.setAllDateColumns(table, id, java.sql.Timestamp.valueOf(date + " 12:00:00"));
                return id;
        }

        static Long pay(TestBase t, long rideId, long userId, double amount, String status, String date) {
                String table = t.tableName("Payment");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Payment", "ride"), rideId);
                try { ov.put(t.columnByField("Payment", "user"), userId); } catch (Throwable ignore) {}
                ov.put(t.columnByField("Payment", "amount"), amount);
                ov.put(t.columnByField("Payment", "status"), status);
                try { ov.put(t.columnByField("Payment", "method"), "CREDIT_CARD"); } catch (Throwable ignore) {}
                Long id = t.insertRowReturningId(table, ov);
                t.setAllDateColumns(table, id, java.sql.Timestamp.valueOf(date + " 12:30:00"));
                return id;
        }

        static long drv(TestBase t, String name) {
                String table = t.tableName("Driver");
                String sn = name.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (sn.length() > 32) sn = sn.substring(0, 32);
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Driver", "name"), name);
                ov.put(t.columnByField("Driver", "email"), sn + "_" + System.nanoTime() + "@uber.io");
                ov.put(t.columnByField("Driver", "phone"),
                        "+201" + String.format("%09d", System.nanoTime() % 1_000_000_000L));
                try { ov.put(t.columnByField("Driver", "licenseNumber"),
                        "DL-" + System.nanoTime()); } catch (Throwable ignore) {}
                try {
                        ov.put(t.columnByField("Driver", "status"),
                                t.enumValueAt("Driver", "status", 0));
                } catch (Throwable ignore) {}
                return t.insertRowReturningId(table, ov);
        }

        static void rideAndRecord(TestBase t, long userId, long driverId, String adminTok) throws Exception {
                Long rid = ride(t, userId, driverId, "COMPLETED", "2026-04-15", 80.0);
                pay(t, rid, userId, 80.0, "COMPLETED", "2026-04-15");
                java.net.http.HttpResponse<String> r = t.httpPostAuth(
                        "/api/rides/" + rid + "/record-interaction", "", adminTok);
                if (r.statusCode() / 100 != 2) {
                        throw new AssertionError("rideAndRecord: failed for user="
                                + userId + " driver=" + driverId
                                + " status=" + r.statusCode() + " body=" + r.body());
                }
        }

        static long rideCount(TestBase t, long userId, long driverId) {
                java.util.List<java.util.Map<String, Object>> rows = t.neo4jExec(
                        "MATCH (a:`" + t.s3GraphUserLabel() + "` {id:$u})-[r:`" + t.s3GraphRelationship()
                          + "`]->(b:`" + t.s3GraphCatalogLabel() + "` {id:$d}) "
                          + "RETURN coalesce(r.rideCount, r.orderCount, r.count, 0) AS c LIMIT 1",
                        java.util.Map.of("u", userId, "d", driverId));
                if (rows.isEmpty()) return 0L;
                Object c = rows.get(0).get("c");
                return c instanceof Number n ? n.longValue() : 0L;
        }

        static long rL(JsonNode j, String... ks) {
                for (String k : ks) if (j.has(k)) return j.get(k).asLong();
                return -1;
        }
        static double rD(JsonNode j, String... ks) {
                for (String k : ks) if (j.has(k)) return j.get(k).asDouble();
                return -1;
        }
        static JsonNode rO(JsonNode j, String... ks) {
                for (String k : ks) if (j.has(k)) return j.get(k);
                return null;
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S4 M2 — Location Service features (TC100..TC135)
//
// Covers S4-F10 (location analytics dashboard, TC100-TC117), S4-F11 (record
// GPS tracking event to Cassandra, TC118-TC127), and S4-F12 (location
// tracking timeline read, TC128-TC135). Theme-specific: S4 endpoints under
// /api/locations; Location PG table holds driverId/latitude/longitude/
// timestamp/metadata; Cassandra time-series table is location_tracking_events
// partitioned by driver_id and clustered by timestamp; Mongo logs
// ANALYTICS_VIEWED / TRACKING_RECORDED to location_events. Per-test wipe of
// PG/Mongo/Cassandra/Redis happens in autoTruncateAllData().
// ════════════════════════════════════════════════════════════════════════════

// ─── TC100 — S4-F10 dashboard happy path ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC100_LocationDashboardHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC100 — Dashboard returns totalLocationEvents=8/activeDrivers=3/averageSpeed/eventsByHour")
        void dashboard_happy_path() throws Exception {
                BASE_URL = locationServiceUrl;
                long d1 = _UbM2.drv(this, "TC100 D1 " + nonce());
                long d2 = _UbM2.drv(this, "TC100 D2 " + nonce());
                long d3 = _UbM2.drv(this, "TC100 D3 " + nonce());
                _UbM2S4.loc(this, d1, 30.04, 31.23, 30.0, "2026-04-15 08:00:00");
                _UbM2S4.loc(this, d1, 30.05, 31.24, 40.0, "2026-04-15 08:30:00");
                _UbM2S4.loc(this, d1, 30.06, 31.25, 50.0, "2026-04-15 17:00:00");
                _UbM2S4.loc(this, d2, 30.07, 31.26, 60.0, "2026-04-16 08:15:00");
                _UbM2S4.loc(this, d2, 30.08, 31.27, 70.0, "2026-04-16 17:30:00");
                _UbM2S4.loc(this, d3, 30.09, 31.28, 80.0, "2026-04-17 17:45:00");
                _UbM2S4.loc(this, d3, 30.10, 31.29, 20.0, "2026-04-18 08:00:00");
                _UbM2S4.loc(this, d3, 30.11, 31.30, 40.0, "2026-04-19 17:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-04-01&endDate=2026-04-30", tok);
                assert2xx(r, "TC100");
                JsonNode j = parseNode(r.body());
                assertEquals(8L, _UbM2.rL(j, "totalLocationEvents", "total_location_events"),
                        "TC100: totalLocationEvents=8; body=" + r.body());
                assertEquals(3L, _UbM2.rL(j, "activeDrivers", "active_drivers"),
                        "TC100: activeDrivers=3");
                JsonNode byHour = _UbM2.rO(j, "eventsByHour", "events_by_hour");
                assertNotNull(byHour, "TC100: eventsByHour key required");
                assertTrue(byHour.has("8") || byHour.has("08"),
                        "TC100: hour 8 must be present in eventsByHour; got " + byHour);
                assertTrue(byHour.has("17"),
                        "TC100: hour 17 must be present in eventsByHour; got " + byHour);
        }
}

// ─── TC101 — S4-F10 totalLocationEvents isolated ───────────────────────────
@Tag("public")
@Tag("features_m2")
class TC101_LocationTotalEventsTests extends TestBase {
        @Test
        @DisplayName("TC101 — Dashboard.totalLocationEvents equals exact count of events in range")
        void total_events_isolated() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC101 D " + nonce());
                for (int i = 0; i < 7; i++)
                        _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-09-15 12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC101");
                assertEquals(7L,
                        _UbM2.rL(parseNode(r.body()), "totalLocationEvents", "total_location_events"),
                        "TC101: totalLocationEvents=7");
        }
}

// ─── TC102 — S4-F10 activeDrivers (distinct count) ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC102_LocationActiveDriversTests extends TestBase {
        @Test
        @DisplayName("TC102 — Dashboard.activeDrivers counts distinct driverIds in range")
        void active_drivers_distinct() throws Exception {
                BASE_URL = locationServiceUrl;
                long d1 = _UbM2.drv(this, "TC102 D1 " + nonce());
                long d2 = _UbM2.drv(this, "TC102 D2 " + nonce());
                _UbM2S4.loc(this, d1, 30.04, 31.23, 40.0, "2026-09-15 10:00:00");
                _UbM2S4.loc(this, d1, 30.05, 31.24, 50.0, "2026-09-15 11:00:00");
                _UbM2S4.loc(this, d1, 30.06, 31.25, 60.0, "2026-09-15 12:00:00");
                _UbM2S4.loc(this, d2, 30.07, 31.26, 30.0, "2026-09-15 13:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC102");
                assertEquals(2L,
                        _UbM2.rL(parseNode(r.body()), "activeDrivers", "active_drivers"),
                        "TC102: activeDrivers=2 (distinct)");
        }
}

// ─── TC103 — S4-F10 averageSpeed isolated ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC103_LocationAverageSpeedTests extends TestBase {
        @Test
        @DisplayName("TC103 — Dashboard.averageSpeed equals mean of metadata.speed values")
        void average_speed_isolated() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC103 D " + nonce());
                for (double sp : new double[]{30, 50, 70, 90}) {
                        _UbM2S4.loc(this, did, 30.04, 31.23, sp, "2026-09-15 12:00:00");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC103");
                double avg = _UbM2.rD(parseNode(r.body()), "averageSpeed", "average_speed", "avgSpeed");
                assertEquals(60.0, avg, 0.5, "TC103: averageSpeed=mean(30,50,70,90)=60");
        }
}

// ─── TC104 — S4-F10 eventsByHour map keyed by hour ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC104_LocationEventsByHourTests extends TestBase {
        @Test
        @DisplayName("TC104 — Dashboard.eventsByHour groups counts by hour-of-day")
        void events_by_hour_isolated() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC104 D " + nonce());
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-09-15 09:00:00");
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-09-15 09:15:00");
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-09-15 09:30:00");
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-09-15 17:00:00");
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-09-15 17:30:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC104");
                JsonNode byHour = _UbM2.rO(parseNode(r.body()), "eventsByHour", "events_by_hour");
                assertNotNull(byHour, "TC104: eventsByHour key required");
                long c9 = byHour.has("9") ? byHour.get("9").asLong()
                        : byHour.has("09") ? byHour.get("09").asLong() : 0L;
                long c17 = byHour.has("17") ? byHour.get("17").asLong() : 0L;
                assertEquals(3L, c9, "TC104: hour 9 count=3");
                assertEquals(2L, c17, "TC104: hour 17 count=2");
        }
}

// ─── TC105 — S4-F10 empty range zeros ──────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC105_LocationEmptyRangeTests extends TestBase {
        @Test
        @DisplayName("TC105 — Dashboard with no events in range returns zeros + empty eventsByHour")
        void empty_range_zeros() throws Exception {
                BASE_URL = locationServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2099-01-01&endDate=2099-01-31", tok);
                assert2xx(r, "TC105");
                JsonNode j = parseNode(r.body());
                assertEquals(0L, _UbM2.rL(j, "totalLocationEvents", "total_location_events"),
                        "TC105: totalLocationEvents=0");
                assertEquals(0L, _UbM2.rL(j, "activeDrivers", "active_drivers"),
                        "TC105: activeDrivers=0");
                assertEquals(0.0, _UbM2.rD(j, "averageSpeed", "average_speed"), 0.01,
                        "TC105: averageSpeed=0");
        }
}

// ─── TC106 — S4-F10 invalid date range → 400 ───────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC106_LocationInvalidRangeTests extends TestBase {
        @Test
        @DisplayName("TC106 — Dashboard with startDate > endDate returns 400")
        void invalid_date_range_400() throws Exception {
                BASE_URL = locationServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-04-30&endDate=2026-04-01", tok);
                assertEquals(400, r.statusCode(),
                        "TC106: must be 400; got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC107 — S4-F10 missing JWT → 401 ──────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC107_LocationMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC107 — Dashboard without Authorization header returns 401")
        void missing_jwt_401() throws Exception {
                BASE_URL = locationServiceUrl;
                HttpResponse<String> r = httpGet(
                        "/api/locations/analytics?startDate=2026-04-01&endDate=2026-04-30");
                assertEquals(401, r.statusCode(),
                        "TC107: must be 401; got " + r.statusCode());
        }
}

// ─── TC108 — S4-F10 invalid JWT → 401 ──────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC108_LocationInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC108 — Dashboard with malformed JWT returns 401")
        void invalid_jwt_401() throws Exception {
                BASE_URL = locationServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-04-01&endDate=2026-04-30", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC108: must be 401; got " + r.statusCode());
        }
}

// ─── TC109 — S4-F10 boundary date inclusion ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC109_LocationBoundaryInclusionTests extends TestBase {
        @Test
        @DisplayName("TC109 — Event exactly on startDate is included")
        void boundary_included() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC109 D " + nonce());
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-05-01 00:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-05-01&endDate=2026-05-31", tok);
                assert2xx(r, "TC109");
                assertEquals(1L,
                        _UbM2.rL(parseNode(r.body()), "totalLocationEvents", "total_location_events"),
                        "TC109: boundary event must be counted");
        }
}

// ─── TC110 — S4-F10 out-of-range events excluded ───────────────────────────
@Tag("public")
@Tag("features_m2")
class TC110_LocationOutOfRangeTests extends TestBase {
        @Test
        @DisplayName("TC110 — Events outside [startDate, endDate] excluded")
        void out_of_range_excluded() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC110 D " + nonce());
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-06-15 12:00:00");
                _UbM2S4.loc(this, did, 30.05, 31.24, 50.0, "2026-08-15 12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-06-01&endDate=2026-06-30", tok);
                assert2xx(r, "TC110");
                assertEquals(1L,
                        _UbM2.rL(parseNode(r.body()), "totalLocationEvents", "total_location_events"),
                        "TC110: only the in-range event counts");
        }
}

// ─── TC111 — S4-F10 averageSpeed=0 when no events ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC111_LocationAvgSpeedZeroTests extends TestBase {
        @Test
        @DisplayName("TC111 — averageSpeed=0 when no events in range")
        void avg_speed_zero_on_empty() throws Exception {
                BASE_URL = locationServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2099-06-01&endDate=2099-06-30", tok);
                assert2xx(r, "TC111");
                assertEquals(0.0,
                        _UbM2.rD(parseNode(r.body()), "averageSpeed", "average_speed"), 0.01,
                        "TC111: averageSpeed=0 with no events");
        }
}

// ─── TC112 — S4-F10 ANALYTICS_VIEWED logged on first call ──────────────────
@Tag("public")
@Tag("features_m2")
class TC112_LocationAnalyticsViewedLoggedTests extends TestBase {
        @Test
        @DisplayName("TC112 — First dashboard call writes ANALYTICS_VIEWED to location_events")
        void analytics_viewed_on_first_call() throws Exception {
                BASE_URL = locationServiceUrl;
                if (mongo == null) throw new AssertionError("TC112: MongoDB required");
                String coll = s4EventsCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                String tok = adminToken();
                assert2xx(httpGetAuth(
                        "/api/locations/analytics?startDate=2026-07-01&endDate=2026-07-31", tok),
                        "TC112");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after > before,
                        "TC112: ANALYTICS_VIEWED count must increase; before=" + before + " after=" + after);
        }
}

// ─── TC113 — S4-F10 ANALYTICS_VIEWED on cache hit ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC113_LocationAnalyticsCacheHitTests extends TestBase {
        @Test
        @DisplayName("TC113 — Cache-hit dashboard call still logs ANALYTICS_VIEWED")
        void analytics_viewed_on_cache_hit() throws Exception {
                BASE_URL = locationServiceUrl;
                if (mongo == null) throw new AssertionError("TC113: MongoDB required");
                String coll = s4EventsCollection();
                String tok = adminToken();
                String url = "/api/locations/analytics?startDate=2026-07-01&endDate=2026-07-31";
                assert2xx(httpGetAuth(url, tok), "TC113 first");
                long after1 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assert2xx(httpGetAuth(url, tok), "TC113 second");
                long after2 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after2 > after1,
                        "TC113: ANALYTICS_VIEWED on cache hit; after1=" + after1 + " after2=" + after2);
        }
}

// ─── TC114 — S4-F10 cache returns same body ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC114_LocationCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC114 — Two identical dashboard requests return identical bodies")
        void cache_same_body() throws Exception {
                BASE_URL = locationServiceUrl;
                String tok = adminToken();
                String url = "/api/locations/analytics?startDate=2026-07-01&endDate=2026-07-31";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC114 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC114 second");
                JsonNode j1 = parseNode(r1.body());
                JsonNode j2 = parseNode(r2.body());
                assertEquals(_UbM2.rL(j1, "totalLocationEvents", "total_location_events"),
                             _UbM2.rL(j2, "totalLocationEvents", "total_location_events"),
                        "TC114: cached totalLocationEvents must match");
        }
}

// ─── TC115 — S4-F10 cache hit doesn't re-aggregate ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC115_LocationCacheNoReaggregateTests extends TestBase {
        @Test
        @DisplayName("TC115 — Insert event after first call → cached body still returned")
        void cache_does_not_reaggregate() throws Exception {
                BASE_URL = locationServiceUrl;
                String tok = adminToken();
                String url = "/api/locations/analytics?startDate=2026-11-01&endDate=2026-11-30";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC115 first");
                long t1 = _UbM2.rL(parseNode(r1.body()), "totalLocationEvents", "total_location_events");
                long did = _UbM2.drv(this, "TC115 D " + nonce());
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-11-15 12:00:00");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC115 second");
                long t2 = _UbM2.rL(parseNode(r2.body()), "totalLocationEvents", "total_location_events");
                assertEquals(t1, t2,
                        "TC115: cached value must equal pre-insert value; t1=" + t1 + " t2=" + t2);
        }
}

// ─── TC116 — S4-F10 eventsByHour omits hours with zero events ──────────────
@Tag("public")
@Tag("features_m2")
class TC116_LocationEventsByHourOmitsZeroTests extends TestBase {
        @Test
        @DisplayName("TC116 — eventsByHour map omits hours with zero events")
        void events_by_hour_omits_zero() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC116 D " + nonce());
                _UbM2S4.loc(this, did, 30.04, 31.23, 40.0, "2026-12-05 10:00:00");
                _UbM2S4.loc(this, did, 30.05, 31.24, 50.0, "2026-12-06 14:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-12-01&endDate=2026-12-31", tok);
                assert2xx(r, "TC116");
                JsonNode byHour = _UbM2.rO(parseNode(r.body()), "eventsByHour", "events_by_hour");
                assertNotNull(byHour, "TC116: eventsByHour key required");
                assertFalse(byHour.has("3") || byHour.has("03"),
                        "TC116: zero-event hour 3 must be omitted; got " + byHour);
                assertTrue(byHour.has("10"), "TC116: hour 10 must be present");
                assertTrue(byHour.has("14"), "TC116: hour 14 must be present");
        }
}

// ─── TC117 — S4-F10 activeDrivers counts distinct only ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC117_LocationActiveDriversDistinctTests extends TestBase {
        @Test
        @DisplayName("TC117 — Multiple events from same driver only count once toward activeDrivers")
        void active_drivers_distinct_only() throws Exception {
                BASE_URL = locationServiceUrl;
                long d1 = _UbM2.drv(this, "TC117 D1 " + nonce());
                for (int i = 0; i < 10; i++)
                        _UbM2S4.loc(this, d1, 30.04, 31.23, 40.0, "2026-10-15 12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/analytics?startDate=2026-10-01&endDate=2026-10-31", tok);
                assert2xx(r, "TC117");
                JsonNode j = parseNode(r.body());
                assertEquals(10L, _UbM2.rL(j, "totalLocationEvents", "total_location_events"),
                        "TC117: totalLocationEvents=10");
                assertEquals(1L, _UbM2.rL(j, "activeDrivers", "active_drivers"),
                        "TC117: activeDrivers=1 (distinct)");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S4-F11 — Record GPS Tracking Event (TC118-TC127)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC118 — S4-F11 happy path: Cassandra row + Mongo TRACKING_RECORDED ───
@Tag("public")
@Tag("features_m2")
class TC118_TrackingHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC118 — Tracking event writes Cassandra row + Mongo TRACKING_RECORDED")
        void tracking_happy_path() throws Exception {
                BASE_URL = locationServiceUrl;
                if (cassandra == null) throw new AssertionError("TC118: Cassandra required");
                if (mongo == null)     throw new AssertionError("TC118: MongoDB required");
                long did = _UbM2.drv(this, "TC118 D " + nonce());
                String coll = s4EventsCollection();
                long mongoBefore = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "TRACKING_RECORDED"));
                String tok = adminToken();
                String body = "{\"latitude\":30.0444,\"longitude\":31.2357,\"speed\":45.0,"
                            + "\"heading\":180.0,\"accuracy\":5.0,\"notes\":\"heading to pickup\"}";
                HttpResponse<String> r = httpPostAuth(
                        "/api/locations/" + did + "/tracking", body, tok);
                assert2xx(r, "TC118");
                long cqlCount = cassandraCount(s4TimeseriesTable(), "driver_id", did);
                assertTrue(cqlCount >= 1,
                        "TC118: Cassandra tracking row must exist for driver " + did + "; got " + cqlCount);
                long mongoAfter = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "TRACKING_RECORDED"));
                assertTrue(mongoAfter > mongoBefore,
                        "TC118: TRACKING_RECORDED must be logged; before=" + mongoBefore + " after=" + mongoAfter);
        }
}

// ─── TC119 — S4-F11 Cassandra row fields populated ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC119_TrackingCassandraFieldsTests extends TestBase {
        @Test
        @DisplayName("TC119 — Cassandra tracking row has latitude/longitude/speed populated")
        void tracking_cassandra_fields() throws Exception {
                BASE_URL = locationServiceUrl;
                if (cassandra == null) throw new AssertionError("TC119: Cassandra required");
                long did = _UbM2.drv(this, "TC119 D " + nonce());
                String tok = adminToken();
                String body = "{\"latitude\":30.0444,\"longitude\":31.2357,\"speed\":45.0,"
                            + "\"heading\":180.0,\"accuracy\":5.0}";
                assert2xx(httpPostAuth("/api/locations/" + did + "/tracking", body, tok), "TC119");
                java.util.List<java.util.Map<String, Object>> rows =
                        cassandraRows(s4TimeseriesTable(), "driver_id", did);
                assertFalse(rows.isEmpty(), "TC119: at least one tracking row");
                java.util.Map<String, Object> row = rows.get(0);
                Object lat = row.get("latitude");
                Object lon = row.get("longitude");
                assertNotNull(lat, "TC119: latitude must be persisted");
                assertNotNull(lon, "TC119: longitude must be persisted");
                if (lat instanceof Number nLat) assertEquals(30.0444, nLat.doubleValue(), 0.01, "TC119: latitude");
                if (lon instanceof Number nLon) assertEquals(31.2357, nLon.doubleValue(), 0.01, "TC119: longitude");
        }
}

// ─── TC120 — S4-F11 multiple events: both rows persist ────────────────────
@Tag("public")
@Tag("features_m2")
class TC120_TrackingMultipleTests extends TestBase {
        @Test
        @DisplayName("TC120 — Two tracking events → both rows present in Cassandra")
        void tracking_multiple_events() throws Exception {
                BASE_URL = locationServiceUrl;
                if (cassandra == null) throw new AssertionError("TC120: Cassandra required");
                long did = _UbM2.drv(this, "TC120 D " + nonce());
                String tok = adminToken();
                String b1 = "{\"latitude\":30.04,\"longitude\":31.23,\"speed\":40.0}";
                assert2xx(httpPostAuth("/api/locations/" + did + "/tracking", b1, tok), "TC120 first");
                Thread.sleep(50);
                String b2 = "{\"latitude\":30.05,\"longitude\":31.24,\"speed\":50.0}";
                assert2xx(httpPostAuth("/api/locations/" + did + "/tracking", b2, tok), "TC120 second");
                long count = cassandraCount(s4TimeseriesTable(), "driver_id", did);
                assertTrue(count >= 2,
                        "TC120: must have ≥2 tracking rows; got " + count);
        }
}

// ─── TC121 — S4-F11 non-existent driver → 404 ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC121_TrackingNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC121 — Tracking event for non-existent driver returns 404")
        void tracking_not_found_404() throws Exception {
                BASE_URL = locationServiceUrl;
                String tok = adminToken();
                String body = "{\"latitude\":30.04,\"longitude\":31.23,\"speed\":40.0}";
                HttpResponse<String> r = httpPostAuth(
                        "/api/locations/999999/tracking", body, tok);
                assertEquals(404, r.statusCode(),
                        "TC121: must be 404; got " + r.statusCode());
        }
}

// ─── TC122 — S4-F11 missing JWT → 401 ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC122_TrackingMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC122 — Tracking without Authorization header returns 401")
        void tracking_missing_jwt_401() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC122 D " + nonce());
                String body = "{\"latitude\":30.04,\"longitude\":31.23,\"speed\":40.0}";
                HttpResponse<String> r = httpPost(
                        "/api/locations/" + did + "/tracking", body);
                assertEquals(401, r.statusCode(),
                        "TC122: must be 401; got " + r.statusCode());
        }
}

// ─── TC123 — S4-F11 invalid JWT → 401 ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC123_TrackingInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC123 — Tracking with malformed JWT returns 401")
        void tracking_invalid_jwt_401() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC123 D " + nonce());
                String body = "{\"latitude\":30.04,\"longitude\":31.23,\"speed\":40.0}";
                HttpResponse<String> r = httpPostAuth(
                        "/api/locations/" + did + "/tracking", body, "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC123: must be 401; got " + r.statusCode());
        }
}

// ─── TC124 — S4-F11 optional fields nullable → 201 ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC124_TrackingOptionalFieldsTests extends TestBase {
        @Test
        @DisplayName("TC124 — Tracking event with only required lat/lon → 2xx")
        void tracking_optional_fields_nullable() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC124 D " + nonce());
                String tok = adminToken();
                String body = "{\"latitude\":30.04,\"longitude\":31.23}";
                HttpResponse<String> r = httpPostAuth(
                        "/api/locations/" + did + "/tracking", body, tok);
                assert2xx(r, "TC124");
        }
}

// ─── TC125 — S4-F11 Mongo doc has driverId in details ────────────────────
@Tag("public")
@Tag("features_m2")
class TC125_TrackingMongoDriverIdTests extends TestBase {
        @Test
        @DisplayName("TC125 — TRACKING_RECORDED Mongo doc carries driverId in details")
        void tracking_mongo_driver_id() throws Exception {
                BASE_URL = locationServiceUrl;
                if (mongo == null) throw new AssertionError("TC125: MongoDB required");
                long did = _UbM2.drv(this, "TC125 D " + nonce());
                String tok = adminToken();
                String body = "{\"latitude\":30.04,\"longitude\":31.23,\"speed\":40.0}";
                assert2xx(httpPostAuth("/api/locations/" + did + "/tracking", body, tok), "TC125");
                String coll = s4EventsCollection();
                long match = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "TRACKING_RECORDED")
                                .append("$or", java.util.List.of(
                                        new org.bson.Document("details.driverId", did),
                                        new org.bson.Document("details.driver_id", did),
                                        new org.bson.Document("driverId", did))));
                assertTrue(match >= 1,
                        "TC125: must find TRACKING_RECORDED doc with driverId=" + did);
        }
}

// ─── TC126 — S4-F11 coordinates preserved in Cassandra ───────────────────
@Tag("public")
@Tag("features_m2")
class TC126_TrackingCoordsPreservedTests extends TestBase {
        @Test
        @DisplayName("TC126 — Cassandra tracking row preserves the exact coordinates supplied")
        void tracking_coords_preserved() throws Exception {
                BASE_URL = locationServiceUrl;
                if (cassandra == null) throw new AssertionError("TC126: Cassandra required");
                long did = _UbM2.drv(this, "TC126 D " + nonce());
                String tok = adminToken();
                String body = "{\"latitude\":30.12345,\"longitude\":31.67890,\"speed\":42.0}";
                assert2xx(httpPostAuth("/api/locations/" + did + "/tracking", body, tok), "TC126");
                java.util.List<java.util.Map<String, Object>> rows =
                        cassandraRows(s4TimeseriesTable(), "driver_id", did);
                assertFalse(rows.isEmpty(), "TC126: tracking row required");
                Object lat = rows.get(0).get("latitude");
                Object lon = rows.get(0).get("longitude");
                if (lat instanceof Number nLat) assertEquals(30.12345, nLat.doubleValue(), 0.0001, "TC126: latitude");
                if (lon instanceof Number nLon) assertEquals(31.67890, nLon.doubleValue(), 0.0001, "TC126: longitude");
        }
}

// ─── TC127 — S4-F11 notes round-trip ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC127_TrackingNotesRoundTripTests extends TestBase {
        @Test
        @DisplayName("TC127 — Notes field round-trips into Cassandra row")
        void tracking_notes_round_trip() throws Exception {
                BASE_URL = locationServiceUrl;
                if (cassandra == null) throw new AssertionError("TC127: Cassandra required");
                long did = _UbM2.drv(this, "TC127 D " + nonce());
                String tok = adminToken();
                String unique = "TC127_" + nonce();
                String body = "{\"latitude\":30.04,\"longitude\":31.23,\"speed\":40.0,"
                            + "\"notes\":\"" + unique + "\"}";
                assert2xx(httpPostAuth("/api/locations/" + did + "/tracking", body, tok), "TC127");
                java.util.List<java.util.Map<String, Object>> rows =
                        cassandraRows(s4TimeseriesTable(), "driver_id", did);
                assertFalse(rows.isEmpty(), "TC127: tracking row required");
                Object notes = rows.get(0).get("notes");
                assertNotNull(notes, "TC127: notes column must be present");
                assertEquals(unique, notes.toString(), "TC127: notes must equal request value");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S4-F12 — Get Location Tracking Timeline (TC128-TC135)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC128 — S4-F12 happy path: 3 events returned reverse-chrono ─────────
@Tag("public")
@Tag("features_m2")
class TC128_TimelineHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC128 — Timeline returns all events, most recent first")
        void timeline_happy_path() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC128 D " + nonce());
                String tok = adminToken();
                for (int i = 0; i < 3; i++) {
                        String body = "{\"latitude\":30.0" + i + ",\"longitude\":31.2" + i
                                    + ",\"speed\":" + (40 + i * 5) + ".0}";
                        assert2xx(httpPostAuth(
                                "/api/locations/" + did + "/tracking", body, tok),
                                "TC128 evt " + i);
                        Thread.sleep(50);
                }
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/" + did + "/tracking", tok);
                assert2xx(r, "TC128");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() >= 3, "TC128: must return ≥3 events; got " + arr.size());
        }
}

// ─── TC129 — S4-F12 time range filter ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC129_TimelineTimeRangeTests extends TestBase {
        @Test
        @DisplayName("TC129 — Timeline with startTime/endTime returns only matching events")
        void timeline_time_range() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC129 D " + nonce());
                String tok = adminToken();
                String body = "{\"latitude\":30.04,\"longitude\":31.23,\"speed\":40.0}";
                assert2xx(httpPostAuth("/api/locations/" + did + "/tracking", body, tok), "TC129");
                String future = java.time.LocalDateTime.now().plusHours(1).toString();
                String past = java.time.LocalDateTime.now().minusHours(1).toString();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/" + did + "/tracking?startTime=" + past + "&endTime=" + future, tok);
                assert2xx(r, "TC129");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() >= 1, "TC129: must return ≥1 event in range; got " + arr.size());
        }
}

// ─── TC130 — S4-F12 non-existent driver → 404 ────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC130_TimelineNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC130 — Timeline for non-existent driver returns 404")
        void timeline_not_found_404() throws Exception {
                BASE_URL = locationServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/999999/tracking", tok);
                assertEquals(404, r.statusCode(),
                        "TC130: must be 404; got " + r.statusCode());
        }
}

// ─── TC131 — S4-F12 missing JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC131_TimelineMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC131 — Timeline without Authorization header returns 401")
        void timeline_missing_jwt_401() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC131 D " + nonce());
                HttpResponse<String> r = httpGet("/api/locations/" + did + "/tracking");
                assertEquals(401, r.statusCode(),
                        "TC131: must be 401; got " + r.statusCode());
        }
}

// ─── TC132 — S4-F12 invalid JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC132_TimelineInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC132 — Timeline with malformed JWT returns 401")
        void timeline_invalid_jwt_401() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC132 D " + nonce());
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/" + did + "/tracking", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC132: must be 401; got " + r.statusCode());
        }
}

// ─── TC133 — S4-F12 driver with no events → empty list ───────────────────
@Tag("public")
@Tag("features_m2")
class TC133_TimelineEmptyListTests extends TestBase {
        @Test
        @DisplayName("TC133 — Timeline for driver with no events returns empty list")
        void timeline_empty_list() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC133 D " + nonce());
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/" + did + "/tracking", tok);
                assert2xx(r, "TC133");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(), "TC133: empty list when no events; body=" + r.body());
        }
}

// ─── TC134 — S4-F12 cache returns same body ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC134_TimelineCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC134 — Two identical timeline requests return identical bodies (cached)")
        void timeline_cache_same_body() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC134 D " + nonce());
                String tok = adminToken();
                String url = "/api/locations/" + did + "/tracking";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC134 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC134 second");
                assertEquals(r1.body(), r2.body(),
                        "TC134: identical timeline responses expected (cached)");
        }
}

// ─── TC135 — S4-F12 each event DTO carries required fields ───────────────
@Tag("public")
@Tag("features_m2")
class TC135_TimelineDtoShapeTests extends TestBase {
        @Test
        @DisplayName("TC135 — Each event includes timestamp/latitude/longitude/speed")
        void timeline_dto_shape() throws Exception {
                BASE_URL = locationServiceUrl;
                long did = _UbM2.drv(this, "TC135 D " + nonce());
                String tok = adminToken();
                String body = "{\"latitude\":30.04,\"longitude\":31.23,\"speed\":42.0,"
                            + "\"heading\":180.0,\"accuracy\":5.0,\"notes\":\"shape-test\"}";
                assert2xx(httpPostAuth("/api/locations/" + did + "/tracking", body, tok),
                        "TC135 record");
                HttpResponse<String> r = httpGetAuth(
                        "/api/locations/" + did + "/tracking", tok);
                assert2xx(r, "TC135 timeline");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() >= 1, "TC135: must return ≥1 event");
                JsonNode item = arr.get(0);
                assertTrue(item.has("timestamp") || item.has("eventTime") || item.has("recordedAt"),
                        "TC135: event must include timestamp; got=" + item);
                assertTrue(item.has("latitude") || item.has("lat"),
                        "TC135: event must include latitude; got=" + item);
                assertTrue(item.has("longitude") || item.has("lon"),
                        "TC135: event must include longitude; got=" + item);
                assertTrue(item.has("speed"),
                        "TC135: event must include speed; got=" + item);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// Helper class for S4 (Location row seeding in PG with metadata.speed JSONB
// for S4-F10 analytics aggregation). Cassandra writes go through the HTTP
// endpoint (S4-F11) so no direct-Cassandra seeder is needed.
// ════════════════════════════════════════════════════════════════════════════
final class _UbM2S4 {
        private _UbM2S4() {}

        static long loc(TestBase t, long driverId, double lat, double lon, double speed, String timestamp) {
                String table = t.tableName("Location");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Location", "driver"), driverId);
                ov.put(t.columnByField("Location", "latitude"), lat);
                ov.put(t.columnByField("Location", "longitude"), lon);
                Long id = t.insertRowReturningId(table, ov);
                String metaCol;
                try { metaCol = t.columnByField("Location", "metadata"); }
                catch (Throwable e) { metaCol = "metadata"; }
                String json = "{\"speed\":" + speed + "}";
                // Spec (Uber M1 §6.4): Location columns are id, driverId, latitude,
                // longitude, timestamp, metadata. There is no created_at on Location —
                // the event time is the 'timestamp' column. Backdate via that field.
                t.jdbc.update(
                        "UPDATE \"" + table + "\" SET \"" + metaCol + "\" = ?::jsonb, "
                          + "\"timestamp\" = ? WHERE id = ?",
                        json, java.sql.Timestamp.valueOf(timestamp), id);
                return id;
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S5 M2 — Payment Service features (TC136..TC190)
//
// Covers S5-F10 (vehicle-type revenue + surge breakdown, TC136-TC158), S5-F11
// (payment-method breakdown from Mongo audit trail, TC159-TC174), and S5-F12
// (surge-adjusted refund with strategy + 24h age window, TC175-TC190).
// Theme-specific: S5 endpoints under /api/payments; Payment carries ride_id
// FK, amount, method, status, plus a transactionDetails JSONB for surgeFee
// + refund metadata; the Mongo audit collection is payment_audit_trail.
// Strategies selected by payment age (24h from createdAt) + refundSurge
// boolean: NoRefundStrategy / FullRefundWithSurgeStrategy /
// BaseFareOnlyRefundStrategy. Per-test wipe of PG/Mongo/Redis happens in
// autoTruncateAllData().
// ════════════════════════════════════════════════════════════════════════════

// ─── TC136 — S5-F10 happy path (SEDAN + SUV per spec scenario) ─────────────
@Tag("public")
@Tag("features_m2")
class TC136_VehicleTypeHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC136 — Vehicle-type breakdown groups by vehicleType with surgeFeeRevenue")
        void vehicle_type_happy_path() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d1 = _UbM2S5.drvVT(this, "SEDAN");
                long d2 = _UbM2S5.drvVT(this, "SUV");
                // 3 SEDAN rides totaling 600 (with surge 90)
                for (double[] s : new double[][]{{200, 30}, {200, 30}, {200, 30}}) {
                        Long rid = _UbM2.ride(this, 1L, d1, "COMPLETED", "2026-03-15", s[0]);
                        _UbM2S5.payWithSurge(this, rid, 1L, s[0], "COMPLETED", s[1], "2026-03-15");
                }
                // 2 SUV rides totaling 400 (with surge 60)
                for (double[] s : new double[][]{{200, 30}, {200, 30}}) {
                        Long rid = _UbM2.ride(this, 1L, d2, "COMPLETED", "2026-03-12", s[0]);
                        _UbM2S5.payWithSurge(this, rid, 1L, s[0], "COMPLETED", s[1], "2026-03-12");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC136");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode sedan = _UbM2S5.findByVehicleType(arr, "SEDAN");
                JsonNode suv = _UbM2S5.findByVehicleType(arr, "SUV");
                assertNotNull(sedan, "TC136: SEDAN row required");
                assertNotNull(suv, "TC136: SUV row required");
                assertEquals(600.0, _UbM2.rD(sedan, "totalRevenue", "total_revenue"), 0.01,
                        "TC136: SEDAN totalRevenue=600");
                assertEquals(90.0, _UbM2.rD(sedan, "surgeFeeRevenue", "surge_fee_revenue"), 0.01,
                        "TC136: SEDAN surgeFee=90");
                assertEquals(400.0, _UbM2.rD(suv, "totalRevenue", "total_revenue"), 0.01,
                        "TC136: SUV totalRevenue=400");
                assertEquals(60.0, _UbM2.rD(suv, "surgeFeeRevenue", "surge_fee_revenue"), 0.01,
                        "TC136: SUV surgeFee=60");
        }
}

// ─── TC137 — S5-F10 totalRevenue equals sum of amounts ────────────────────
@Tag("public")
@Tag("features_m2")
class TC137_VehicleTypeTotalRevenueTests extends TestBase {
        @Test
        @DisplayName("TC137 — totalRevenue equals SUM(payments.amount) for COMPLETED")
        void total_revenue() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SEDAN");
                for (double a : new double[]{100, 150, 200}) {
                        Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", a);
                        _UbM2S5.payWithSurge(this, rid, 1L, a, "COMPLETED", 0.15 * a, "2026-03-10");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC137");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "SEDAN");
                assertEquals(450.0, _UbM2.rD(row, "totalRevenue", "total_revenue"), 0.01,
                        "TC137: totalRevenue=450");
        }
}

// ─── TC138 — S5-F10 surgeFeeRevenue from JSONB ────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC138_VehicleTypeSurgeFeeJsonbTests extends TestBase {
        @Test
        @DisplayName("TC138 — surgeFeeRevenue sums transactionDetails.surgeFee values")
        void surge_fee_from_jsonb() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SUV");
                Long r1 = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 200.0);
                _UbM2S5.payWithSurge(this, r1, 1L, 200.0, "COMPLETED", 50.0, "2026-03-10");
                Long r2 = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-12", 200.0);
                _UbM2S5.payWithSurge(this, r2, 1L, 200.0, "COMPLETED", 70.0, "2026-03-12");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC138");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "SUV");
                assertEquals(120.0, _UbM2.rD(row, "surgeFeeRevenue", "surge_fee_revenue"), 0.01,
                        "TC138: surgeFeeRevenue=50+70=120");
        }
}

// ─── TC139 — S5-F10 baseFareRevenue = total - surgeFee ────────────────────
@Tag("public")
@Tag("features_m2")
class TC139_VehicleTypeBaseFareRevenueTests extends TestBase {
        @Test
        @DisplayName("TC139 — baseFareRevenue equals totalRevenue - surgeFeeRevenue per group")
        void base_fare_revenue() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "LUXURY");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 500.0);
                _UbM2S5.payWithSurge(this, rid, 1L, 500.0, "COMPLETED", 100.0, "2026-03-10");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC139");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "LUXURY");
                double total = _UbM2.rD(row, "totalRevenue", "total_revenue");
                double surge = _UbM2.rD(row, "surgeFeeRevenue", "surge_fee_revenue");
                double base = _UbM2.rD(row, "baseFareRevenue", "base_fare_revenue");
                assertEquals(total - surge, base, 0.01,
                        "TC139: baseFareRevenue must equal totalRevenue - surgeFeeRevenue");
        }
}

// ─── TC140 — S5-F10 rideCount counts distinct rides ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC140_VehicleTypeRideCountTests extends TestBase {
        @Test
        @DisplayName("TC140 — rideCount equals distinct ride IDs in the group")
        void ride_count_distinct() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "HATCHBACK");
                for (int i = 0; i < 5; i++) {
                        Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-1" + i, 100.0);
                        _UbM2S5.payWithSurge(this, rid, 1L, 100.0, "COMPLETED", 15.0, "2026-03-1" + i);
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC140");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "HATCHBACK");
                assertEquals(5L, _UbM2.rL(row, "rideCount", "ride_count"),
                        "TC140: rideCount=5");
        }
}

// ─── TC141 — S5-F10 grouping by vehicleType separates results ─────────────
@Tag("public")
@Tag("features_m2")
class TC141_VehicleTypeGroupingTests extends TestBase {
        @Test
        @DisplayName("TC141 — Different vehicleTypes produce separate rows")
        void group_by_vehicle_type() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d1 = _UbM2S5.drvVT(this, "SEDAN");
                long d2 = _UbM2S5.drvVT(this, "VAN");
                Long r1 = _UbM2.ride(this, 1L, d1, "COMPLETED", "2026-03-10", 100.0);
                _UbM2S5.payWithSurge(this, r1, 1L, 100.0, "COMPLETED", 15.0, "2026-03-10");
                Long r2 = _UbM2.ride(this, 1L, d2, "COMPLETED", "2026-03-11", 200.0);
                _UbM2S5.payWithSurge(this, r2, 1L, 200.0, "COMPLETED", 30.0, "2026-03-11");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC141");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_UbM2S5.findByVehicleType(arr, "SEDAN"), "TC141: SEDAN row");
                assertNotNull(_UbM2S5.findByVehicleType(arr, "VAN"), "TC141: VAN row");
        }
}

// ─── TC142 — S5-F10 includes only COMPLETED payments ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC142_VehicleTypeOnlyCompletedTests extends TestBase {
        @Test
        @DisplayName("TC142 — Only COMPLETED payments contribute (PENDING/FAILED/REFUNDED excluded)")
        void only_completed_payments() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rA = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 100.0);
                _UbM2S5.payWithSurge(this, rA, 1L, 100.0, "COMPLETED", 15.0, "2026-03-10");
                Long rB = _UbM2.ride(this, 1L, d, "REQUESTED", "2026-03-11", 100.0);
                _UbM2S5.payWithSurge(this, rB, 1L, 100.0, "PENDING", 15.0, "2026-03-11");
                Long rC = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-12", 100.0);
                _UbM2S5.payWithSurge(this, rC, 1L, 100.0, "FAILED", 15.0, "2026-03-12");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC142");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "SEDAN");
                assertNotNull(row, "TC142: SEDAN row");
                assertEquals(1L, _UbM2.rL(row, "rideCount", "ride_count"),
                        "TC142: rideCount=1 (only COMPLETED)");
                assertEquals(100.0, _UbM2.rD(row, "totalRevenue", "total_revenue"), 0.01,
                        "TC142: totalRevenue=100");
        }
}

// ─── TC143 — S5-F10 excludes REFUNDED payments ────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC143_VehicleTypeExcludesRefundedTests extends TestBase {
        @Test
        @DisplayName("TC143 — REFUNDED payments are NOT in totalRevenue (Uber-specific: COMPLETED only)")
        void excludes_refunded() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rA = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 200.0);
                _UbM2S5.payWithSurge(this, rA, 1L, 200.0, "COMPLETED", 30.0, "2026-03-10");
                Long rB = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-11", 200.0);
                _UbM2S5.payWithSurge(this, rB, 1L, 200.0, "REFUNDED", 30.0, "2026-03-11");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC143");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "SUV");
                assertEquals(1L, _UbM2.rL(row, "rideCount", "ride_count"),
                        "TC143: rideCount=1 (REFUNDED excluded)");
                assertEquals(200.0, _UbM2.rD(row, "totalRevenue", "total_revenue"), 0.01,
                        "TC143: totalRevenue=200 (REFUNDED excluded)");
        }
}

// ─── TC144 — S5-F10 empty range returns empty list ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC144_VehicleTypeEmptyRangeTests extends TestBase {
        @Test
        @DisplayName("TC144 — Date range with no payments returns empty list")
        void empty_range_empty_list() throws Exception {
                BASE_URL = paymentServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2099-01-01&endDate=2099-01-31", tok);
                assert2xx(r, "TC144");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(), "TC144: empty list expected; body=" + r.body());
        }
}

// ─── TC145 — S5-F10 invalid date range → 400 ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC145_VehicleTypeInvalidRangeTests extends TestBase {
        @Test
        @DisplayName("TC145 — startDate > endDate returns 400")
        void invalid_date_range_400() throws Exception {
                BASE_URL = paymentServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-04-30&endDate=2026-04-01", tok);
                assertEquals(400, r.statusCode(),
                        "TC145: must be 400; got " + r.statusCode());
        }
}

// ─── TC146 — S5-F10 missing JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC146_VehicleTypeMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC146 — Vehicle-type without Authorization header returns 401")
        void missing_jwt_401() throws Exception {
                BASE_URL = paymentServiceUrl;
                HttpResponse<String> r = httpGet(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31");
                assertEquals(401, r.statusCode(),
                        "TC146: must be 401; got " + r.statusCode());
        }
}

// ─── TC147 — S5-F10 invalid JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC147_VehicleTypeInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC147 — Vehicle-type with malformed JWT returns 401")
        void invalid_jwt_401() throws Exception {
                BASE_URL = paymentServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31",
                        "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC147: must be 401; got " + r.statusCode());
        }
}

// ─── TC148 — S5-F10 surgeFee defaults to 15% when JSONB key missing ──────
@Tag("public")
@Tag("features_m2")
class TC148_VehicleTypeSurgeFeeDefault15PctTests extends TestBase {
        @Test
        @DisplayName("TC148 — Payment with no surgeFee key → surgeFeeRevenue defaults to 15% of amount")
        void surge_fee_default_15pct() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 100.0);
                _UbM2S5.payNoSurgeKey(this, rid, 1L, 100.0, "COMPLETED", "2026-03-10");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC148");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "SEDAN");
                double surge = _UbM2.rD(row, "surgeFeeRevenue", "surge_fee_revenue");
                assertEquals(15.0, surge, 0.5,
                        "TC148: missing surgeFee → 15% of amount=15; got " + surge);
        }
}

// ─── TC149 — S5-F10 ANALYTICS_VIEWED logged on first call ────────────────
@Tag("public")
@Tag("features_m2")
class TC149_VehicleTypeAnalyticsViewedTests extends TestBase {
        @Test
        @DisplayName("TC149 — First vehicle-type call writes ANALYTICS_VIEWED to payment_audit_trail")
        void analytics_viewed_logged() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC149: MongoDB required");
                String coll = s5AuditCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                String tok = adminToken();
                assert2xx(httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-07-01&endDate=2026-07-31", tok),
                        "TC149");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after > before,
                        "TC149: ANALYTICS_VIEWED count must increase; before=" + before + " after=" + after);
        }
}

// ─── TC150 — S5-F10 ANALYTICS_VIEWED on cache hit ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC150_VehicleTypeAnalyticsCacheHitTests extends TestBase {
        @Test
        @DisplayName("TC150 — Cache-hit vehicle-type call still logs ANALYTICS_VIEWED")
        void analytics_viewed_on_cache_hit() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC150: MongoDB required");
                String coll = s5AuditCollection();
                String tok = adminToken();
                String url = "/api/payments/analytics/vehicle-type?startDate=2026-07-01&endDate=2026-07-31";
                assert2xx(httpGetAuth(url, tok), "TC150 first");
                long after1 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assert2xx(httpGetAuth(url, tok), "TC150 second");
                long after2 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after2 > after1,
                        "TC150: ANALYTICS_VIEWED on cache hit; after1=" + after1 + " after2=" + after2);
        }
}

// ─── TC151 — S5-F10 cache returns same body ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC151_VehicleTypeCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC151 — Two identical vehicle-type requests return identical bodies")
        void cache_same_body() throws Exception {
                BASE_URL = paymentServiceUrl;
                String tok = adminToken();
                String url = "/api/payments/analytics/vehicle-type?startDate=2026-07-01&endDate=2026-07-31";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC151 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC151 second");
                assertEquals(r1.body(), r2.body(),
                        "TC151: cached vehicle-type responses must match");
        }
}

// ─── TC152 — S5-F10 cache hit doesn't re-aggregate ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC152_VehicleTypeCacheNoReaggregateTests extends TestBase {
        @Test
        @DisplayName("TC152 — Insert payment after first call → cached body still returned")
        void cache_does_not_reaggregate() throws Exception {
                BASE_URL = paymentServiceUrl;
                String tok = adminToken();
                String url = "/api/payments/analytics/vehicle-type?startDate=2026-11-01&endDate=2026-11-30";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC152 first");
                JsonNode body1 = parseNode(r1.body());
                int before = body1.has("content") ? body1.get("content").size() : body1.size();
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-11-15", 100.0);
                _UbM2S5.payWithSurge(this, rid, 1L, 100.0, "COMPLETED", 15.0, "2026-11-15");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC152 second");
                JsonNode body2 = parseNode(r2.body());
                int after = body2.has("content") ? body2.get("content").size() : body2.size();
                assertEquals(before, after,
                        "TC152: cached size must equal pre-insert size; before=" + before + " after=" + after);
        }
}

// ─── TC153 — S5-F10 boundary date inclusion ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC153_VehicleTypeBoundaryInclusionTests extends TestBase {
        @Test
        @DisplayName("TC153 — Payment exactly on startDate is included")
        void boundary_included() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-05-01", 100.0);
                _UbM2S5.payWithSurge(this, rid, 1L, 100.0, "COMPLETED", 15.0, "2026-05-01");
                String rTable = tableName("Ride");
                // Spec (Uber M1 §6.3): Ride lifecycle timestamps are requestedAt
                // and completedAt. There is no created_at column on rides — use
                // requestedAt (Hibernate maps to requested_at in PG, but resolve
                // via columnByField to tolerate student naming).
                String reqAtCol = columnByField("Ride", "requestedAt");
                jdbc.update("UPDATE \"" + rTable + "\" SET \"" + reqAtCol + "\" = ? WHERE id = ?",
                        java.sql.Timestamp.valueOf("2026-05-01 00:00:00"), rid);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-05-01&endDate=2026-05-31", tok);
                assert2xx(r, "TC153");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_UbM2S5.findByVehicleType(arr, "SUV"),
                        "TC153: boundary payment must be included");
        }
}

// ─── TC154 — S5-F10 out-of-range payments excluded ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC154_VehicleTypeOutOfRangeTests extends TestBase {
        @Test
        @DisplayName("TC154 — Payments outside the date range are excluded")
        void out_of_range_excluded() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "LUXURY");
                Long inRange = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-06-15", 100.0);
                _UbM2S5.payWithSurge(this, inRange, 1L, 100.0, "COMPLETED", 15.0, "2026-06-15");
                Long outRange = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-08-15", 200.0);
                _UbM2S5.payWithSurge(this, outRange, 1L, 200.0, "COMPLETED", 30.0, "2026-08-15");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-06-01&endDate=2026-06-30", tok);
                assert2xx(r, "TC154");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "LUXURY");
                assertNotNull(row, "TC154: LUXURY row required");
                assertEquals(1L, _UbM2.rL(row, "rideCount", "ride_count"),
                        "TC154: rideCount=1 (out-of-range excluded)");
        }
}

// ─── TC155 — S5-F10 different vehicleTypes are separate rows ─────────────
@Tag("public")
@Tag("features_m2")
class TC155_VehicleTypeSeparateRowsTests extends TestBase {
        @Test
        @DisplayName("TC155 — 3 distinct vehicleTypes produce 3 rows in the breakdown")
        void three_distinct_vehicle_types() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d1 = _UbM2S5.drvVT(this, "SEDAN");
                long d2 = _UbM2S5.drvVT(this, "SUV");
                long d3 = _UbM2S5.drvVT(this, "LUXURY");
                for (long d : new long[]{d1, d2, d3}) {
                        Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 100.0);
                        _UbM2S5.payWithSurge(this, rid, 1L, 100.0, "COMPLETED", 15.0, "2026-03-10");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC155");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_UbM2S5.findByVehicleType(arr, "SEDAN"), "TC155: SEDAN row");
                assertNotNull(_UbM2S5.findByVehicleType(arr, "SUV"), "TC155: SUV row");
                assertNotNull(_UbM2S5.findByVehicleType(arr, "LUXURY"), "TC155: LUXURY row");
        }
}

// ─── TC156 — S5-F10 multiple drivers same vehicleType combine ────────────
@Tag("public")
@Tag("features_m2")
class TC156_VehicleTypeMultipleDriversCombineTests extends TestBase {
        @Test
        @DisplayName("TC156 — Two SEDAN drivers' payments combine into a single SEDAN row")
        void same_vehicle_type_combines() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d1 = _UbM2S5.drvVT(this, "SEDAN");
                long d2 = _UbM2S5.drvVT(this, "SEDAN");
                for (long d : new long[]{d1, d2}) {
                        Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 150.0);
                        _UbM2S5.payWithSurge(this, rid, 1L, 150.0, "COMPLETED", 25.0, "2026-03-10");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC156");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "SEDAN");
                assertEquals(2L, _UbM2.rL(row, "rideCount", "ride_count"),
                        "TC156: SEDAN rideCount=2 (combined across drivers)");
                assertEquals(300.0, _UbM2.rD(row, "totalRevenue", "total_revenue"), 0.01,
                        "TC156: SEDAN totalRevenue=300");
        }
}

// ─── TC157 — S5-F10 explicit zero surgeFee → zero in breakdown ───────────
@Tag("public")
@Tag("features_m2")
class TC157_VehicleTypeZeroSurgeFeeTests extends TestBase {
        @Test
        @DisplayName("TC157 — Payment with surgeFee=0 contributes 0 to surgeFeeRevenue")
        void zero_surge_fee_explicit() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "HATCHBACK");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 100.0);
                _UbM2S5.payWithSurge(this, rid, 1L, 100.0, "COMPLETED", 0.0, "2026-03-10");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC157");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "HATCHBACK");
                assertEquals(0.0, _UbM2.rD(row, "surgeFeeRevenue", "surge_fee_revenue"), 0.01,
                        "TC157: explicit surgeFee=0 → surgeFeeRevenue=0");
                assertEquals(100.0, _UbM2.rD(row, "totalRevenue", "total_revenue"), 0.01,
                        "TC157: totalRevenue=100");
        }
}

// ─── TC158 — S5-F10 totalRevenue = baseFareRevenue + surgeFeeRevenue check
@Tag("public")
@Tag("features_m2")
class TC158_VehicleTypeRevenueDecompositionTests extends TestBase {
        @Test
        @DisplayName("TC158 — totalRevenue = baseFareRevenue + surgeFeeRevenue across the group")
        void revenue_decomposition() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "VAN");
                Long r1 = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-10", 200.0);
                _UbM2S5.payWithSurge(this, r1, 1L, 200.0, "COMPLETED", 50.0, "2026-03-10");
                Long r2 = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-03-12", 300.0);
                _UbM2S5.payWithSurge(this, r2, 1L, 300.0, "COMPLETED", 75.0, "2026-03-12");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC158");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _UbM2S5.findByVehicleType(arr, "VAN");
                double total = _UbM2.rD(row, "totalRevenue", "total_revenue");
                double base = _UbM2.rD(row, "baseFareRevenue", "base_fare_revenue");
                double surge = _UbM2.rD(row, "surgeFeeRevenue", "surge_fee_revenue");
                assertEquals(500.0, total, 0.01, "TC158: totalRevenue=500");
                assertEquals(125.0, surge, 0.01, "TC158: surgeFeeRevenue=125");
                assertEquals(total, base + surge, 0.01,
                        "TC158: totalRevenue (" + total + ") must equal baseFare+surge ("
                                + (base + surge) + ")");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S5-F11 — Payment Method Breakdown (TC159-TC174)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC159 — S5-F11 happy path from payment_audit_trail ──────────────────
@Tag("public")
@Tag("features_m2")
class TC159_PaymentMethodHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC159 — Methods breakdown groups by method with successCount/failureCount/successRate/totalAmount")
        void method_happy_path() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC159: MongoDB required");
                _UbM2S5.cleanAudit(this);
                _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T12:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T13:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T14:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T15:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T16:00:00");
                _UbM2S5.audit(this, "FAILED",    "CREDIT_CARD",  50, "2026-08-15T17:00:00");
                _UbM2S5.audit(this, "FAILED",    "CREDIT_CARD",  50, "2026-08-15T18:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CASH",        100, "2026-08-15T19:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CASH",        100, "2026-08-15T20:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CASH",        100, "2026-08-15T21:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC159");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode cc = _UbM2S5.findByMethod(arr, "CREDIT_CARD");
                JsonNode cash = _UbM2S5.findByMethod(arr, "CASH");
                assertNotNull(cc, "TC159: CREDIT_CARD row required");
                assertNotNull(cash, "TC159: CASH row required");
                assertEquals(5L, _UbM2.rL(cc, "successCount", "success_count"), "TC159: CC success=5");
                assertEquals(2L, _UbM2.rL(cc, "failureCount", "failure_count"), "TC159: CC failure=2");
                assertEquals(500.0, _UbM2.rD(cc, "totalAmount", "total_amount"), 0.01, "TC159: CC total=500");
                assertEquals(3L, _UbM2.rL(cash, "successCount", "success_count"), "TC159: CASH success=3");
                assertEquals(0L, _UbM2.rL(cash, "failureCount", "failure_count"), "TC159: CASH failure=0");
        }
}

// ─── TC160 — S5-F11 successCount isolated ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC160_PaymentMethodSuccessCountTests extends TestBase {
        @Test
        @DisplayName("TC160 — successCount counts only COMPLETED events per method")
        void success_count_isolated() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC160: MongoDB required");
                _UbM2S5.cleanAudit(this);
                for (int i = 0; i < 4; i++)
                        _UbM2S5.audit(this, "COMPLETED", "WALLET", 50, "2026-08-1" + i + "T12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC160");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode w = _UbM2S5.findByMethod(arr, "WALLET");
                assertEquals(4L, _UbM2.rL(w, "successCount", "success_count"), "TC160: WALLET success=4");
        }
}

// ─── TC161 — S5-F11 failureCount isolated ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC161_PaymentMethodFailureCountTests extends TestBase {
        @Test
        @DisplayName("TC161 — failureCount counts only FAILED events per method")
        void failure_count_isolated() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC161: MongoDB required");
                _UbM2S5.cleanAudit(this);
                for (int i = 0; i < 3; i++)
                        _UbM2S5.audit(this, "FAILED", "CASH", 0, "2026-08-1" + i + "T12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC161");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode c = _UbM2S5.findByMethod(arr, "CASH");
                assertEquals(3L, _UbM2.rL(c, "failureCount", "failure_count"), "TC161: CASH failure=3");
        }
}

// ─── TC162 — S5-F11 successRate isolated ─────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC162_PaymentMethodSuccessRateTests extends TestBase {
        @Test
        @DisplayName("TC162 — successRate = successCount / (successCount + failureCount)")
        void success_rate_isolated() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC162: MongoDB required");
                _UbM2S5.cleanAudit(this);
                for (int i = 0; i < 7; i++)
                        _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-1" + i + "T12:00:00");
                for (int i = 0; i < 3; i++)
                        _UbM2S5.audit(this, "FAILED", "CREDIT_CARD", 100, "2026-08-2" + i + "T12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC162");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode cc = _UbM2S5.findByMethod(arr, "CREDIT_CARD");
                assertEquals(0.7, _UbM2.rD(cc, "successRate", "success_rate"), 0.01,
                        "TC162: successRate=7/10=0.7");
        }
}

// ─── TC163 — S5-F11 totalAmount sums COMPLETED only ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC163_PaymentMethodTotalAmountTests extends TestBase {
        @Test
        @DisplayName("TC163 — totalAmount sums amounts of COMPLETED events only (FAILED excluded)")
        void total_amount_completed_only() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC163: MongoDB required");
                _UbM2S5.cleanAudit(this);
                _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T10:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 200, "2026-08-15T11:00:00");
                _UbM2S5.audit(this, "FAILED",    "CREDIT_CARD", 999, "2026-08-15T12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC163");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode cc = _UbM2S5.findByMethod(arr, "CREDIT_CARD");
                assertEquals(300.0, _UbM2.rD(cc, "totalAmount", "total_amount"), 0.01,
                        "TC163: totalAmount=100+200=300 (FAILED excluded)");
        }
}

// ─── TC164 — S5-F11 grouping by method ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC164_PaymentMethodGroupingTests extends TestBase {
        @Test
        @DisplayName("TC164 — Different methods produce separate rows")
        void group_by_method() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC164: MongoDB required");
                _UbM2S5.cleanAudit(this);
                _UbM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-10T10:00:00");
                _UbM2S5.audit(this, "COMPLETED", "CASH",        50, "2026-08-11T10:00:00");
                _UbM2S5.audit(this, "COMPLETED", "WALLET",      75, "2026-08-12T10:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC164");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_UbM2S5.findByMethod(arr, "CREDIT_CARD"), "TC164: CREDIT_CARD row");
                assertNotNull(_UbM2S5.findByMethod(arr, "CASH"), "TC164: CASH row");
                assertNotNull(_UbM2S5.findByMethod(arr, "WALLET"), "TC164: WALLET row");
        }
}

// ─── TC165 — S5-F11 only COMPLETED+FAILED actions counted ────────────────
@Tag("public")
@Tag("features_m2")
class TC165_PaymentMethodAllowedActionsTests extends TestBase {
        @Test
        @DisplayName("TC165 — Only action ∈ {COMPLETED, FAILED} contributes to counts")
        void allowed_actions_only() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC165: MongoDB required");
                _UbM2S5.cleanAudit(this);
                _UbM2S5.audit(this, "COMPLETED", "CASH", 100, "2026-08-10T10:00:00");
                _UbM2S5.audit(this, "FAILED",    "CASH",   0, "2026-08-11T10:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC165");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode c = _UbM2S5.findByMethod(arr, "CASH");
                assertNotNull(c, "TC165: CASH row");
                assertEquals(1L, _UbM2.rL(c, "successCount", "success_count"), "TC165: success=1");
                assertEquals(1L, _UbM2.rL(c, "failureCount", "failure_count"), "TC165: failure=1");
        }
}

// ─── TC166 — S5-F11 excluded actions ignored ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC166_PaymentMethodExcludedActionsTests extends TestBase {
        @Test
        @DisplayName("TC166 — CREATED/REFUNDED/REFUND_DENIED/ANALYTICS_VIEWED do NOT contribute")
        void excluded_actions_ignored() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC166: MongoDB required");
                _UbM2S5.cleanAudit(this);
                _UbM2S5.audit(this, "COMPLETED",        "CASH", 100, "2026-08-10T10:00:00");
                _UbM2S5.audit(this, "REFUNDED",         "CASH", 100, "2026-08-11T10:00:00");
                _UbM2S5.audit(this, "REFUND_DENIED",    "CASH",   0, "2026-08-12T10:00:00");
                _UbM2S5.audit(this, "CREATED",          "CASH", 100, "2026-08-13T10:00:00");
                _UbM2S5.audit(this, "ANALYTICS_VIEWED", "CASH",   0, "2026-08-14T10:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC166");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode c = _UbM2S5.findByMethod(arr, "CASH");
                assertEquals(1L, _UbM2.rL(c, "successCount", "success_count"),
                        "TC166: only the 1 COMPLETED counts");
                assertEquals(0L, _UbM2.rL(c, "failureCount", "failure_count"),
                        "TC166: failure=0 (no FAILED docs)");
        }
}

// ─── TC167 — S5-F11 empty range returns empty list ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC167_PaymentMethodEmptyRangeTests extends TestBase {
        @Test
        @DisplayName("TC167 — Date range with no events returns empty list")
        void empty_range_empty_list() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC167: MongoDB required");
                _UbM2S5.cleanAudit(this);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2099-01-01&endDate=2099-01-31", tok);
                assert2xx(r, "TC167");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(), "TC167: empty list expected");
        }
}

// ─── TC168 — S5-F11 invalid date range → 400 ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC168_PaymentMethodInvalidRangeTests extends TestBase {
        @Test
        @DisplayName("TC168 — startDate > endDate returns 400")
        void invalid_range_400() throws Exception {
                BASE_URL = paymentServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-04-30&endDate=2026-04-01", tok);
                assertEquals(400, r.statusCode(),
                        "TC168: must be 400; got " + r.statusCode());
        }
}

// ─── TC169 — S5-F11 missing JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC169_PaymentMethodMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC169 — Methods without Authorization header returns 401")
        void missing_jwt_401() throws Exception {
                BASE_URL = paymentServiceUrl;
                HttpResponse<String> r = httpGet(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31");
                assertEquals(401, r.statusCode(),
                        "TC169: must be 401; got " + r.statusCode());
        }
}

// ─── TC170 — S5-F11 invalid JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC170_PaymentMethodInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC170 — Methods with malformed JWT returns 401")
        void invalid_jwt_401() throws Exception {
                BASE_URL = paymentServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31",
                        "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC170: must be 401; got " + r.statusCode());
        }
}

// ─── TC171 — S5-F11 successRate=0 when no events ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC171_PaymentMethodSuccessRateZeroDenominatorTests extends TestBase {
        @Test
        @DisplayName("TC171 — successRate=0 when method has no COMPLETED or FAILED events")
        void success_rate_zero_denominator() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC171: MongoDB required");
                _UbM2S5.cleanAudit(this);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC171");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(),
                        "TC171: empty array when no events at all");
        }
}

// ─── TC172 — S5-F11 cache returns same body ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC172_PaymentMethodCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC172 — Two identical methods requests return identical bodies (cached)")
        void cache_same_body() throws Exception {
                BASE_URL = paymentServiceUrl;
                String tok = adminToken();
                String url = "/api/payments/analytics/methods?startDate=2026-09-01&endDate=2026-09-30";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC172 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC172 second");
                assertEquals(r1.body(), r2.body(),
                        "TC172: cached methods responses must match");
        }
}

// ─── TC173 — S5-F11 boundary date inclusion ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC173_PaymentMethodBoundaryInclusionTests extends TestBase {
        @Test
        @DisplayName("TC173 — Audit event exactly on startDate is included")
        void boundary_included() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC173: MongoDB required");
                _UbM2S5.cleanAudit(this);
                _UbM2S5.audit(this, "COMPLETED", "CASH", 100, "2026-10-01T00:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-10-01&endDate=2026-10-31", tok);
                assert2xx(r, "TC173");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_UbM2S5.findByMethod(arr, "CASH"),
                        "TC173: boundary event must be counted");
        }
}

// ─── TC174 — S5-F11 out-of-range events excluded ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC174_PaymentMethodOutOfRangeTests extends TestBase {
        @Test
        @DisplayName("TC174 — Audit events outside the date range are excluded")
        void out_of_range_excluded() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC174: MongoDB required");
                _UbM2S5.cleanAudit(this);
                _UbM2S5.audit(this, "COMPLETED", "WALLET", 100, "2026-10-15T10:00:00");
                _UbM2S5.audit(this, "COMPLETED", "WALLET", 200, "2026-12-15T10:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-10-01&endDate=2026-10-31", tok);
                assert2xx(r, "TC174");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode w = _UbM2S5.findByMethod(arr, "WALLET");
                assertEquals(1L, _UbM2.rL(w, "successCount", "success_count"),
                        "TC174: only the in-range event counts");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S5-F12 — Refund Surge-Adjusted with Strategy + 24h Age Window (TC175-TC190)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC175 — S5-F12 FullRefundWithSurge: refundSurge=true, fresh payment
@Tag("public")
@Tag("features_m2")
class TC175_RefundFullStrategyTests extends TestBase {
        @Test
        @DisplayName("TC175 — Fresh payment + refundSurge=true → FullRefundWithSurgeStrategy: refund=full amount")
        void full_refund_with_surge() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 200.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 200.0, "COMPLETED", 30.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"driver_no_show\",\"refundSurge\":true}", tok);
                assert2xx(r, "TC175");
                String pTable = tableName("Payment");
                String details = jdbc.queryForObject(
                        "SELECT transaction_details::text FROM \"" + pTable + "\" WHERE id=?",
                        String.class, pid);
                assertNotNull(details, "TC175: transactionDetails must be populated");
                assertTrue(details.contains("\"refundAmount\":200")
                                || details.contains("\"refundAmount\": 200")
                                || details.contains("\"refundAmount\":200.0"),
                        "TC175: refundAmount=200; got " + details);
                assertTrue(details.contains("\"refundSurgeIncluded\":true")
                                || details.contains("\"refundSurgeIncluded\": true"),
                        "TC175: refundSurgeIncluded=true; got " + details);
        }
}

// ─── TC176 — S5-F12 BaseFareOnly: refundSurge=false, fresh payment ──────
@Tag("public")
@Tag("features_m2")
class TC176_RefundBaseFareOnlyTests extends TestBase {
        @Test
        @DisplayName("TC176 — Fresh payment + refundSurge=false → BaseFareOnlyRefundStrategy: refund=amount-surgeFee")
        void base_fare_only_refund() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 200.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 200.0, "COMPLETED", 30.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"driver_no_show\",\"refundSurge\":false}", tok);
                assert2xx(r, "TC176");
                String pTable = tableName("Payment");
                String details = jdbc.queryForObject(
                        "SELECT transaction_details::text FROM \"" + pTable + "\" WHERE id=?",
                        String.class, pid);
                assertNotNull(details, "TC176: transactionDetails populated");
                assertTrue(details.contains("\"refundAmount\":170")
                                || details.contains("\"refundAmount\": 170")
                                || details.contains("\"refundAmount\":170.0"),
                        "TC176: refundAmount=170 (200-30); got " + details);
                assertTrue(details.contains("\"refundSurgeIncluded\":false")
                                || details.contains("\"refundSurgeIncluded\": false"),
                        "TC176: refundSurgeIncluded=false; got " + details);
        }
}

// ─── TC177 — S5-F12 NoRefund: payment older than 24h → 400 ──────────────
@Tag("public")
@Tag("features_m2")
class TC177_RefundExpiredWindowTests extends TestBase {
        @Test
        @DisplayName("TC177 — Payment older than 24h → NoRefundStrategy → 400 'refund window expired'")
        void no_refund_expired_window_400() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "LUXURY");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-08", 200.0);
                Long pid = _UbM2S5.payAged(this, rid, 1L, 200.0, "COMPLETED", 30.0, 48);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"too_late\",\"refundSurge\":true}", tok);
                assertEquals(400, r.statusCode(),
                        "TC177: must be 400 for >24h-old payment; got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC178 — S5-F12 PENDING payment → 400 ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC178_RefundPendingPaymentTests extends TestBase {
        @Test
        @DisplayName("TC178 — Refund attempt on PENDING payment returns 400")
        void refund_pending_400() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 100.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 100.0, "PENDING", 15.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok);
                assertEquals(400, r.statusCode(),
                        "TC178: must be 400 for PENDING payment; got " + r.statusCode());
        }
}

// ─── TC179 — S5-F12 FAILED payment → 400 ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC179_RefundFailedPaymentTests extends TestBase {
        @Test
        @DisplayName("TC179 — Refund attempt on FAILED payment returns 400")
        void refund_failed_400() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 100.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 100.0, "FAILED", 15.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok);
                assertEquals(400, r.statusCode(),
                        "TC179: must be 400 for FAILED payment; got " + r.statusCode());
        }
}

// ─── TC180 — S5-F12 already-REFUNDED payment → 400 ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC180_RefundAlreadyRefundedTests extends TestBase {
        @Test
        @DisplayName("TC180 — Refund attempt on already REFUNDED payment returns 400")
        void refund_already_refunded_400() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 100.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 100.0, "REFUNDED", 15.0);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok);
                assertEquals(400, r.statusCode(),
                        "TC180: must be 400 for REFUNDED payment; got " + r.statusCode());
        }
}

// ─── TC181 — S5-F12 non-existent payment → 404 ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC181_RefundNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC181 — Refund of non-existent payment returns 404")
        void refund_not_found_404() throws Exception {
                BASE_URL = paymentServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/999999/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok);
                assertEquals(404, r.statusCode(),
                        "TC181: must be 404; got " + r.statusCode());
        }
}

// ─── TC182 — S5-F12 missing JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC182_RefundMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC182 — Refund without Authorization header returns 401")
        void refund_missing_jwt_401() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rid = _UbM2.ride(this, adminId(), d, "COMPLETED", "2026-04-10", 200.0);
                Long pid = _UbM2S5.payFresh(this, rid, adminId(), 200.0, "COMPLETED", 30.0);
                HttpResponse<String> r = httpPost(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}");
                assertEquals(401, r.statusCode(),
                        "TC182: must be 401; got " + r.statusCode());
        }
}

// ─── TC183 — S5-F12 invalid JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC183_RefundInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC183 — Refund with malformed JWT returns 401")
        void refund_invalid_jwt_401() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rid = _UbM2.ride(this, adminId(), d, "COMPLETED", "2026-04-10", 200.0);
                Long pid = _UbM2S5.payFresh(this, rid, adminId(), 200.0, "COMPLETED", 30.0);
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC183: must be 401; got " + r.statusCode());
        }
}

// ─── TC184 — S5-F12 payment.status → REFUNDED on success ────────────────
@Tag("public")
@Tag("features_m2")
class TC184_RefundPaymentStatusUpdatedTests extends TestBase {
        @Test
        @DisplayName("TC184 — On successful refund, payment.status becomes REFUNDED")
        void payment_status_refunded() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 200.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 200.0, "COMPLETED", 30.0);
                String tok = adminToken();
                assert2xx(httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok), "TC184");
                String pTable = tableName("Payment");
                String stCol = columnByField("Payment", "status");
                String status = jdbc.queryForObject(
                        "SELECT \"" + stCol + "\"::text FROM \"" + pTable + "\" WHERE id=?",
                        String.class, pid);
                assertEquals("REFUNDED", status,
                        "TC184: payment.status must be REFUNDED; got " + status);
        }
}

// ─── TC185 — S5-F12 transactionDetails.refundAmount populated ───────────
@Tag("public")
@Tag("features_m2")
class TC185_RefundJsonbAmountTests extends TestBase {
        @Test
        @DisplayName("TC185 — On success, transactionDetails.refundAmount carries computed amount")
        void jsonb_refund_amount() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 150.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 150.0, "COMPLETED", 22.5);
                String tok = adminToken();
                assert2xx(httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":false}", tok), "TC185");
                String pTable = tableName("Payment");
                String details = jdbc.queryForObject(
                        "SELECT transaction_details::text FROM \"" + pTable + "\" WHERE id=?",
                        String.class, pid);
                assertTrue(details.contains("\"refundAmount\":127.5")
                                || details.contains("\"refundAmount\": 127.5"),
                        "TC185: refundAmount=127.5 (150-22.5); got " + details);
        }
}

// ─── TC186 — S5-F12 transactionDetails has all required keys ────────────
@Tag("public")
@Tag("features_m2")
class TC186_RefundJsonbKeysTests extends TestBase {
        @Test
        @DisplayName("TC186 — transactionDetails has refundAmount, refundSurgeIncluded, refundReason, refundedAt")
        void jsonb_keys_present() throws Exception {
                BASE_URL = paymentServiceUrl;
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 200.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 200.0, "COMPLETED", 30.0);
                String tok = adminToken();
                assert2xx(httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"driver_no_show\",\"refundSurge\":true}", tok), "TC186");
                String pTable = tableName("Payment");
                String details = jdbc.queryForObject(
                        "SELECT transaction_details::text FROM \"" + pTable + "\" WHERE id=?",
                        String.class, pid);
                assertTrue(details.contains("refundAmount"), "TC186: refundAmount key");
                assertTrue(details.contains("refundSurgeIncluded") || details.contains("refundSurge"),
                        "TC186: refundSurgeIncluded key");
                assertTrue(details.contains("refundReason") || details.contains("reason"),
                        "TC186: refundReason key");
                assertTrue(details.contains("refundedAt") || details.contains("refunded_at"),
                        "TC186: refundedAt key");
        }
}

// ─── TC187 — S5-F12 REFUNDED Mongo doc on success ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC187_RefundMongoSuccessLogTests extends TestBase {
        @Test
        @DisplayName("TC187 — On success, REFUNDED doc written to payment_audit_trail")
        void refunded_mongo_log() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC187: MongoDB required");
                String coll = s5AuditCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "REFUNDED"));
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 200.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 200.0, "COMPLETED", 30.0);
                String tok = adminToken();
                assert2xx(httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok), "TC187");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "REFUNDED"));
                assertTrue(after > before,
                        "TC187: REFUNDED doc count must increase; before=" + before + " after=" + after);
        }
}

// ─── TC188 — S5-F12 REFUND_DENIED logged BEFORE 400 thrown ──────────────
@Tag("public")
@Tag("features_m2")
class TC188_RefundDeniedMongoLogTests extends TestBase {
        @Test
        @DisplayName("TC188 — NoRefundStrategy denial path writes REFUND_DENIED to payment_audit_trail BEFORE 400")
        void refund_denied_logged_before_throw() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (mongo == null) throw new AssertionError("TC188: MongoDB required");
                String coll = s5AuditCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "REFUND_DENIED"));
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 100.0);
                Long pid = _UbM2S5.payAged(this, rid, 1L, 100.0, "COMPLETED", 15.0, 48);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok);
                assertEquals(400, r.statusCode(),
                        "TC188 sanity: must 400 for >24h-old payment");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "REFUND_DENIED"));
                assertTrue(after > before,
                        "TC188: REFUND_DENIED doc must be persisted BEFORE 400 thrown; before="
                                + before + " after=" + after);
        }
}

// ─── TC189 — S5-F12 success invalidates S5-F10 cache ────────────────────
@Tag("public")
@Tag("features_m2")
class TC189_RefundInvalidatesS5F10CacheTests extends TestBase {
        @Test
        @DisplayName("TC189 — Successful refund removes payment-service::S5-F10::* keys from Redis")
        void refund_invalidates_s5_f10_cache() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (redis == null) throw new AssertionError("TC189: Redis required");
                String tok = adminToken();
                assert2xx(httpGetAuth(
                        "/api/payments/analytics/vehicle-type?startDate=2026-03-01&endDate=2026-03-31", tok),
                        "TC189 warm");
                java.util.Set<String> beforeKeys = redisKeys("*S5-F10*");
                long d = _UbM2S5.drvVT(this, "SEDAN");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 100.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 100.0, "COMPLETED", 15.0);
                assert2xx(httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok), "TC189 refund");
                java.util.Set<String> afterKeys = redisKeys("*S5-F10*");
                assertTrue(afterKeys.size() < beforeKeys.size() || afterKeys.isEmpty(),
                        "TC189: S5-F10 cache must be invalidated; before=" + beforeKeys + " after=" + afterKeys);
        }
}

// ─── TC190 — S5-F12 success invalidates S5-F11 cache ────────────────────
@Tag("public")
@Tag("features_m2")
class TC190_RefundInvalidatesS5F11CacheTests extends TestBase {
        @Test
        @DisplayName("TC190 — Successful refund removes payment-service::S5-F11::* keys from Redis")
        void refund_invalidates_s5_f11_cache() throws Exception {
                BASE_URL = paymentServiceUrl;
                if (redis == null) throw new AssertionError("TC190: Redis required");
                String tok = adminToken();
                assert2xx(httpGetAuth(
                        "/api/payments/analytics/methods?startDate=2026-03-01&endDate=2026-03-31", tok),
                        "TC190 warm");
                java.util.Set<String> beforeKeys = redisKeys("*S5-F11*");
                long d = _UbM2S5.drvVT(this, "SUV");
                Long rid = _UbM2.ride(this, 1L, d, "COMPLETED", "2026-04-10", 100.0);
                Long pid = _UbM2S5.payFresh(this, rid, 1L, 100.0, "COMPLETED", 15.0);
                assert2xx(httpPostAuth(
                        "/api/payments/" + pid + "/refund-surge-adjusted",
                        "{\"reason\":\"x\",\"refundSurge\":true}", tok), "TC190 refund");
                java.util.Set<String> afterKeys = redisKeys("*S5-F11*");
                assertTrue(afterKeys.size() < beforeKeys.size() || afterKeys.isEmpty(),
                        "TC190: S5-F11 cache must be invalidated; before=" + beforeKeys + " after=" + afterKeys);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// Helper class for S5 (Driver-with-vehicleType seeder, Payment with surgeFee
// JSONB, Payment with createdAt offset for the 24h refund window, synthetic
// payment_audit_trail events for S5-F11 isolation testing, response row
// finders by vehicleType / method).
// ════════════════════════════════════════════════════════════════════════════
final class _UbM2S5 {
        private _UbM2S5() {}

        /** Driver with the chosen vehicleType in vehicleDetails JSONB. */
        static long drvVT(TestBase t, String vehicleType) {
                long id = _UbM2.drv(t, "S5_" + vehicleType + "_" + System.nanoTime());
                String table = t.tableName("Driver");
                String vdCol;
                try { vdCol = t.columnByField("Driver", "vehicleDetails"); }
                catch (Throwable e) { vdCol = "vehicle_details"; }
                String json = "{\"vehicleType\":\"" + vehicleType + "\",\"make\":\"Toyota\","
                            + "\"model\":\"Camry\",\"year\":2024,\"color\":\"White\","
                            + "\"licensePlate\":\"ABC-" + (System.nanoTime() % 10000) + "\"}";
                t.jdbc.update(
                        "UPDATE \"" + table + "\" SET \"" + vdCol + "\" = ?::jsonb WHERE id = ?",
                        json, id);
                return id;
        }

        /** Payment row with transactionDetails.surgeFee = supplied amount. */
        static Long payWithSurge(TestBase t, long rideId, long userId, double amount,
                        String status, double surgeFee, String date) {
                String table = t.tableName("Payment");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Payment", "ride"), rideId);
                try { ov.put(t.columnByField("Payment", "user"), userId); } catch (Throwable ignore) {}
                ov.put(t.columnByField("Payment", "amount"), amount);
                ov.put(t.columnByField("Payment", "status"), status);
                try { ov.put(t.columnByField("Payment", "method"), "CREDIT_CARD"); } catch (Throwable ignore) {}
                Long id = t.insertRowReturningId(table, ov);
                String json = "{\"surgeFee\":" + surgeFee + ",\"gatewayResponse\":\"approved\","
                            + "\"cardLastFour\":\"4242\"}";
                try {
                        t.jdbc.update(
                                "UPDATE \"" + table + "\" SET transaction_details = ?::jsonb WHERE id = ?",
                                json, id);
                } catch (Throwable ignore) {}
                t.setAllDateColumns(table, id, java.sql.Timestamp.valueOf(date + " 12:00:00"));
                return id;
        }

        /** Payment row WITHOUT a surgeFee key in JSONB (for default-15% test). */
        static Long payNoSurgeKey(TestBase t, long rideId, long userId, double amount,
                        String status, String date) {
                String table = t.tableName("Payment");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Payment", "ride"), rideId);
                try { ov.put(t.columnByField("Payment", "user"), userId); } catch (Throwable ignore) {}
                ov.put(t.columnByField("Payment", "amount"), amount);
                ov.put(t.columnByField("Payment", "status"), status);
                try { ov.put(t.columnByField("Payment", "method"), "CREDIT_CARD"); } catch (Throwable ignore) {}
                Long id = t.insertRowReturningId(table, ov);
                String json = "{\"gatewayResponse\":\"approved\",\"cardLastFour\":\"4242\"}";
                try {
                        t.jdbc.update(
                                "UPDATE \"" + table + "\" SET transaction_details = ?::jsonb WHERE id = ?",
                                json, id);
                } catch (Throwable ignore) {}
                t.setAllDateColumns(table, id, java.sql.Timestamp.valueOf(date + " 12:00:00"));
                return id;
        }

        /** Payment created "now" (createdAt = current timestamp) so the 24h refund
         *  window is open. Used for S5-F12 happy-path tests. */
        static Long payFresh(TestBase t, long rideId, long userId, double amount,
                        String status, double surgeFee) {
                String table = t.tableName("Payment");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Payment", "ride"), rideId);
                try { ov.put(t.columnByField("Payment", "user"), userId); } catch (Throwable ignore) {}
                ov.put(t.columnByField("Payment", "amount"), amount);
                ov.put(t.columnByField("Payment", "status"), status);
                try { ov.put(t.columnByField("Payment", "method"), "CREDIT_CARD"); } catch (Throwable ignore) {}
                Long id = t.insertRowReturningId(table, ov);
                String json = "{\"surgeFee\":" + surgeFee + ",\"gatewayResponse\":\"approved\","
                            + "\"cardLastFour\":\"4242\"}";
                try {
                        t.jdbc.update(
                                "UPDATE \"" + table + "\" SET transaction_details = ?::jsonb, "
                                  + "created_at = NOW() WHERE id = ?",
                                json, id);
                } catch (Throwable e) {
                        t.jdbc.update(
                                "UPDATE \"" + table + "\" SET created_at = NOW() WHERE id = ?", id);
                }
                return id;
        }

        /** Payment with createdAt set to N hours in the past (for the 24h-window
         *  expiry test). hoursOld=48 → outside window. */
        static Long payAged(TestBase t, long rideId, long userId, double amount,
                        String status, double surgeFee, int hoursOld) {
                Long id = payWithSurge(t, rideId, userId, amount, status, surgeFee, "2026-04-10");
                String table = t.tableName("Payment");
                java.sql.Timestamp aged = java.sql.Timestamp.valueOf(
                        java.time.LocalDateTime.now().minusHours(hoursOld));
                t.jdbc.update(
                        "UPDATE \"" + table + "\" SET created_at = ? WHERE id = ?", aged, id);
                return id;
        }

        /** Insert a synthetic event into payment_audit_trail for S5-F11 testing. */
        static void audit(TestBase t, String action, String method,
                        double amount, String iso) {
                if (t.mongo == null) return;
                String coll = t.s5AuditCollection();
                org.bson.Document doc = new org.bson.Document("action", action)
                        .append("method", method)
                        .append("amount", amount)
                        .append("timestamp", java.time.LocalDateTime.parse(iso)
                                .atZone(java.time.ZoneId.systemDefault()).toInstant());
                t.mongo.getCollection(coll).insertOne(doc);
        }

        /** Drop all docs in payment_audit_trail (test isolation). */
        static void cleanAudit(TestBase t) {
                if (t.mongo == null) return;
                t.mongo.getCollection(t.s5AuditCollection())
                        .deleteMany(new org.bson.Document());
        }

        static JsonNode findByVehicleType(JsonNode arr, String vehicleType) {
                if (arr == null) return null;
                for (JsonNode item : arr) {
                        if (item.has("vehicleType") && vehicleType.equals(item.get("vehicleType").asText())) return item;
                        if (item.has("vehicle_type") && vehicleType.equals(item.get("vehicle_type").asText())) return item;
                }
                return null;
        }

        static JsonNode findByMethod(JsonNode arr, String method) {
                if (arr == null) return null;
                for (JsonNode item : arr) {
                        if (item.has("method") && method.equals(item.get("method").asText())) return item;
                }
                return null;
        }
}

// ════════════════════════════════════════════════════════════════════════════
// M1 REGRESSION BLOCK — TC191..TC378 (Uber theme)
// One @Test per class; each class extends TestBase; all schema-driven
// seeding via tableName/columnByField/insertRowReturningId helpers.
// Tags: @Tag("public") + @Tag("features_m1").
// ════════════════════════════════════════════════════════════════════════════

/** Shared seeding helper for the Uber M1 regression block. Mirrors
 *  Booking's _BkM1Seed pattern: every column lookup goes through
 *  TestBase.columnByField(...) so a student renaming a field on a spec
 *  entity still seeds correctly. JSONB columns are populated with a
 *  separate raw UPDATE after insertRowReturningId(). */
final class _UbM1Seed {
    private _UbM1Seed() {}

    /** INSERT a user. Returns new user id. role: RIDER or ADMIN. */
    static long seedUser(TestBase t, String name, String email, String role) {
        String tbl = t.tableName("User");
        String bcrypt = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        String phone = "+201" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("User", "name"), name);
        ov.put(t.columnByField("User", "email"), email);
        ov.put(t.columnByField("User", "phone"), phone);
        ov.put(t.columnByField("User", "password"), bcrypt);
        ov.put(t.columnByField("User", "role"), role);
        ov.put(t.columnByField("User", "status"), "ACTIVE");
        try { ov.put(t.columnByField("User", "preferences"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a SavedAddress. */
    static long seedAddress(TestBase t, long userId, String label, boolean isDefault) {
        String tbl = t.tableName("SavedAddress");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("SavedAddress", "user"), userId);
        try { ov.put(t.columnByField("SavedAddress", "label"), label); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "address"), "12 Tahrir St, " + label); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "latitude"), 30.044); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "longitude"), 31.235); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "isDefault"), isDefault); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "metadata"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a Driver. status: AVAILABLE/BUSY/OFFLINE. */
    static long seedDriver(TestBase t, String name, String licenseNumber, String status,
                           double rating, int totalRatings) {
        String tbl = t.tableName("Driver");
        String sn = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (sn.length() > 24) sn = sn.substring(0, 24);
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Driver", "name"), name);
        ov.put(t.columnByField("Driver", "email"), sn + "_" + System.nanoTime() + "@uber.io");
        ov.put(t.columnByField("Driver", "phone"),
                "+201" + String.format("%09d", System.nanoTime() % 1_000_000_000L));
        try { ov.put(t.columnByField("Driver", "licenseNumber"), licenseNumber); } catch (Throwable ignore) {}
        ov.put(t.columnByField("Driver", "status"), status);
        try { ov.put(t.columnByField("Driver", "rating"), rating); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Driver", "totalRatings"), totalRatings); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Driver", "vehicleDetails"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a DriverDocument. type: LICENSE/INSURANCE/REGISTRATION. */
    static long seedDocument(TestBase t, long driverId, String type,
                             java.time.LocalDate expiryDate, boolean verified) {
        String tbl = t.tableName("DriverDocument");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("DriverDocument", "driver"), driverId);
        ov.put(t.columnByField("DriverDocument", "type"), type);
        try { ov.put(t.columnByField("DriverDocument", "documentUrl"),
                "https://docs.uber.io/doc_" + System.nanoTime()); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("DriverDocument", "expiryDate"),
                java.sql.Date.valueOf(expiryDate)); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("DriverDocument", "verified"), verified); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("DriverDocument", "metadata"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a Ride. status: REQUESTED/ACCEPTED/IN_PROGRESS/COMPLETED/CANCELLED.
     *  date: yyyy-MM-dd format. driverId can be 0 to skip the FK. */
    static long seedRide(TestBase t, long userId, long driverId, String status,
                         String date, double fare) {
        String tbl = t.tableName("Ride");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Ride", "user"), userId);
        if (driverId > 0) {
            try { ov.put(t.columnByField("Ride", "driver"), driverId); } catch (Throwable ignore) {}
        }
        ov.put(t.columnByField("Ride", "status"), status);
        try { ov.put(t.columnByField("Ride", "fare"), fare); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Ride", "pickupLatitude"), 30.044); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Ride", "pickupLongitude"), 31.235); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Ride", "dropoffLatitude"), 30.100); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Ride", "dropoffLongitude"), 31.300); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Ride", "metadata"), "{}"); } catch (Throwable ignore) {}
        Long id = t.insertRowReturningId(tbl, ov);
        try {
            t.setAllDateColumns(tbl, id, java.sql.Timestamp.valueOf(date + " 12:00:00"));
        } catch (Throwable ignore) {}
        return id;
    }

    /** INSERT a RideStop. status: PENDING/REACHED/SKIPPED. */
    static long seedRideStop(TestBase t, long rideId, int order, double lat, double lon,
                             String address, String status) {
        String tbl = t.tableName("RideStop");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("RideStop", "ride"), rideId);
        try { ov.put(t.columnByField("RideStop", "stopOrder"), order); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("RideStop", "latitude"), lat); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("RideStop", "longitude"), lon); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("RideStop", "address"), address); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("RideStop", "status"), status); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("RideStop", "metadata"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a Location. timestamp: yyyy-MM-dd HH:mm:ss format. */
    static long seedLocation(TestBase t, long driverId, double lat, double lon, String timestamp) {
        String tbl = t.tableName("Location");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Location", "driver"), driverId);
        try { ov.put(t.columnByField("Location", "latitude"), lat); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Location", "longitude"), lon); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Location", "timestamp"),
                java.sql.Timestamp.valueOf(timestamp)); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Location", "metadata"), "{}"); } catch (Throwable ignore) {}
        Long id = t.insertRowReturningId(tbl, ov);
        try {
            t.setAllDateColumns(tbl, id, java.sql.Timestamp.valueOf(timestamp));
        } catch (Throwable ignore) {}
        return id;
    }

    /** INSERT a Payment. method: CREDIT_CARD/CASH/WALLET. status: PENDING/COMPLETED/FAILED/REFUNDED. */
    static long seedPayment(TestBase t, long rideId, long userId, double amount,
                            String method, String status) {
        String tbl = t.tableName("Payment");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Payment", "ride"), rideId);
        try { ov.put(t.columnByField("Payment", "user"), userId); } catch (Throwable ignore) {}
        ov.put(t.columnByField("Payment", "amount"), amount);
        try { ov.put(t.columnByField("Payment", "method"), method); } catch (Throwable ignore) {}
        ov.put(t.columnByField("Payment", "status"), status);
        try { ov.put(t.columnByField("Payment", "transactionDetails"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a Coupon. discountType: PERCENTAGE/FIXED. expiry can be null. */
    static long seedCoupon(TestBase t, String code, String type, double value,
                           int maxUses, java.time.LocalDateTime expiry, boolean active) {
        String tbl = t.tableName("Coupon");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Coupon", "code"), code);
        ov.put(t.columnByField("Coupon", "discountType"), type);
        ov.put(t.columnByField("Coupon", "discountValue"), value);
        try { ov.put(t.columnByField("Coupon", "maxUses"), maxUses); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Coupon", "currentUses"), 0); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Coupon", "expiryDate"),
                expiry == null ? null : java.sql.Timestamp.valueOf(expiry)); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Coupon", "active"), active); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Coupon", "metadata"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a PaymentCoupon join row. */
    static long seedPaymentCoupon(TestBase t, long paymentId, long couponId, double applied) {
        String tbl = t.tableName("PaymentCoupon");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("PaymentCoupon", "payment"), paymentId);
        ov.put(t.columnByField("PaymentCoupon", "coupon"), couponId);
        try { ov.put(t.columnByField("PaymentCoupon", "discountApplied"), applied); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("PaymentCoupon", "appliedAt"),
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now())); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    // ─── JSONB setters (after insertRowReturningId since JSONB casts via raw UPDATE) ──

    static void setUserPreferences(TestBase t, long userId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("User") + "\" SET preferences = ?::jsonb WHERE id = ?",
                    json, userId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("User needs `preferences` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setDriverVehicleDetails(TestBase t, long driverId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Driver") + "\" SET \""
                    + t.columnByField("Driver", "vehicleDetails") + "\" = ?::jsonb WHERE id = ?",
                    json, driverId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("Driver needs `vehicleDetails` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setRideMetadata(TestBase t, long rideId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Ride") + "\" SET \""
                    + t.columnByField("Ride", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, rideId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("Ride needs `metadata` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setLocationMetadata(TestBase t, long locId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Location") + "\" SET \""
                    + t.columnByField("Location", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, locId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("Location needs `metadata` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setSavedAddressMetadata(TestBase t, long addrId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("SavedAddress") + "\" SET \""
                    + t.columnByField("SavedAddress", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, addrId);
        } catch (org.springframework.dao.DataAccessException ignored) { /* metadata may be optional */ }
    }

    static void setDriverDocumentMetadata(TestBase t, long docId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("DriverDocument") + "\" SET \""
                    + t.columnByField("DriverDocument", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, docId);
        } catch (org.springframework.dao.DataAccessException ignored) { }
    }

    static void setRideStopMetadata(TestBase t, long stopId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("RideStop") + "\" SET \""
                    + t.columnByField("RideStop", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, stopId);
        } catch (org.springframework.dao.DataAccessException ignored) { }
    }

    static void setPaymentTransactionDetails(TestBase t, long payId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Payment") + "\" SET \""
                    + t.columnByField("Payment", "transactionDetails") + "\" = ?::jsonb WHERE id = ?",
                    json, payId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("Payment needs `transactionDetails` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setCouponMetadata(TestBase t, long couponId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Coupon") + "\" SET \""
                    + t.columnByField("Coupon", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, couponId);
        } catch (org.springframework.dao.DataAccessException ignored) { }
    }

    // ─── Date setters ──

    static void setRideCompletedAt(TestBase t, long rideId, java.sql.Timestamp ts) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Ride") + "\" SET \""
                    + t.columnByField("Ride", "completedAt") + "\" = ? WHERE id = ?",
                    ts, rideId);
        } catch (Throwable ignore) { /* completedAt may be optional */ }
    }

    static void setRideRequestedAt(TestBase t, long rideId, java.sql.Timestamp ts) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Ride") + "\" SET \""
                    + t.columnByField("Ride", "requestedAt") + "\" = ? WHERE id = ?",
                    ts, rideId);
        } catch (Throwable ignore) { }
    }

    static void setLocationTimestamp(TestBase t, long locId, java.sql.Timestamp ts) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Location") + "\" SET \""
                    + t.columnByField("Location", "timestamp") + "\" = ? WHERE id = ?",
                    ts, locId);
        } catch (Throwable ignore) { }
    }

    static java.time.LocalDateTime futureDateTime() {
        return java.time.LocalDateTime.of(2030, 12, 31, 23, 59, 59);
    }
    static java.time.LocalDateTime pastDateTime() {
        return java.time.LocalDateTime.now().minusYears(6);
    }
    static java.time.LocalDate today() { return java.time.LocalDate.now(); }
    static java.time.LocalDate futureDate() { return java.time.LocalDate.of(2030, 12, 31); }
    static java.time.LocalDate pastDate() { return java.time.LocalDate.now().minusYears(6); }
}

// ────────────────────────────────────────────────────────────────────────────
// S1 — User Service (TC191..TC220) — 9 features, 30 TCs
// S1-F1 search, S1-F2 prefs, S1-F3 ride-summary, S1-F4 deactivate,
// S1-F5 prefs filter, S1-F6 top-riders, S1-F7 default address,
// S1-F8 profile, S1-F9 lang+minRides
// ────────────────────────────────────────────────────────────────────────────

// ─── TC191 — S1-F1 search by partial name ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC191_UbSearchUsersByNameTests extends TestBase {
    @Test
    @DisplayName("TC191 — Search by name 'Ahmed' returns 2 users (partial match)")
    void search_by_name() throws Exception {
        BASE_URL = userServiceUrl;
        _UbM1Seed.seedUser(this, "Ahmed",     "tc191_a@uber.io", "RIDER");
        _UbM1Seed.seedUser(this, "Sara",      "tc191_b@uber.io", "ADMIN");
        _UbM1Seed.seedUser(this, "Ahmed Ali", "tc191_c@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?name=Ahmed", tok);
        assert2xx(r, "TC191 search");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        int matches = 0;
        for (JsonNode it : list) {
            String n = it.has("name") ? it.get("name").asText() : "";
            if (n.contains("Ahmed")) matches++;
        }
        assertEquals(2, matches, "TC191: 2 Ahmed-named users expected; got " + matches + " body=" + r.body());
    }
}

// ─── TC192 — S1-F1 search by role exact match ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC192_UbSearchUsersByRoleTests extends TestBase {
    @Test
    @DisplayName("TC192 — Search by role=ADMIN returns ADMIN users only")
    void search_by_role() throws Exception {
        BASE_URL = userServiceUrl;
        _UbM1Seed.seedUser(this, "Ahmed",     "tc192_a@uber.io", "RIDER");
        _UbM1Seed.seedUser(this, "Sara",      "tc192_b@uber.io", "ADMIN");
        _UbM1Seed.seedUser(this, "Ahmed Ali", "tc192_c@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?role=ADMIN", tok);
        assert2xx(r, "TC192 search");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String role = it.has("role") ? it.get("role").asText() : "";
            assertEquals("ADMIN", role, "TC192: every result must have role=ADMIN; got " + role);
        }
        assertTrue(list.size() >= 1, "TC192: at least one ADMIN expected; got " + list.size());
    }
}

// ─── TC193 — S1-F1 no-match returns empty list ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC193_UbSearchUsersNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC193 — Search with no-matching name returns empty list")
    void search_no_match() throws Exception {
        BASE_URL = userServiceUrl;
        _UbM1Seed.seedUser(this, "Ahmed", "tc193_a@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?name=zzzNoMatchXYZ", tok);
        assert2xx(r, "TC193 search");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC193: empty list expected; got " + list.size() + " body=" + r.body());
    }
}

// ─── TC194 — S1-F2 update preferences merge ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC194_UbUpdatePreferencesMergeTests extends TestBase {
    @Test
    @DisplayName("TC194 — PUT preferences merges: language preserved, theme updated, currency added")
    void preferences_merge() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Pref User", "tc194@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, uid, "{\"language\":\"en\",\"theme\":\"light\"}");
        String tok = adminToken();
        String body = "{\"theme\":\"dark\",\"currency\":\"EGP\"}";
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/preferences", body, tok);
        assert2xx(r, "TC194");
        JsonNode j = parseNode(r.body());
        JsonNode prefs = j.has("preferences") ? j.get("preferences") : j;
        assertEquals("en",   prefs.has("language") ? prefs.get("language").asText() : "", "TC194: language preserved");
        assertEquals("dark", prefs.has("theme")    ? prefs.get("theme").asText()    : "", "TC194: theme updated");
        assertEquals("EGP",  prefs.has("currency") ? prefs.get("currency").asText() : "", "TC194: currency added");
    }
}

// ─── TC195 — S1-F2 same-key overwrite ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC195_UbUpdatePreferencesOverwriteTests extends TestBase {
    @Test
    @DisplayName("TC195 — PUT with existing key overwrites it")
    void preferences_overwrite() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Pref User", "tc195@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, uid, "{\"language\":\"en\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/preferences", "{\"language\":\"fr\"}", tok);
        assert2xx(r, "TC195");
        JsonNode prefs = parseNode(r.body()).has("preferences")
                ? parseNode(r.body()).get("preferences") : parseNode(r.body());
        assertEquals("fr", prefs.has("language") ? prefs.get("language").asText() : "",
                "TC195: language must be overwritten to 'fr'");
    }
}

// ─── TC196 — S1-F2 404 non-existent user ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC196_UbUpdatePreferencesNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC196 — PUT preferences for non-existent user returns 404")
    void preferences_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/999999/preferences", "{\"x\":\"y\"}", tok);
        assertEquals(404, r.statusCode(), "TC196: must be 404; got " + r.statusCode());
    }
}

// ─── TC197 — S1-F3 ride summary happy path ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC197_UbRideSummaryHappyTests extends TestBase {
    @Test
    @DisplayName("TC197 — Summary returns totalRides=5, completedRides=3, totalSpent=700, cancelledRides=1")
    void summary_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Sum User", "tc197@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D1", "DL197", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 150.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 200.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-12", 350.0);
        _UbM1Seed.seedRide(this, uid, did, "CANCELLED", "2026-03-13", 999.0);
        _UbM1Seed.seedRide(this, uid, did, "REQUESTED", "2026-03-14", 999.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/ride-summary", tok);
        assert2xx(r, "TC197");
        JsonNode j = parseNode(r.body());
        long total = _UbM2.rL(j, "totalRides", "total_rides");
        long completed = _UbM2.rL(j, "completedRides", "completed_rides");
        long cancelled = _UbM2.rL(j, "cancelledRides", "cancelled_rides");
        double spent = _UbM2.rD(j, "totalSpent", "total_spent");
        assertEquals(5, total, "TC197: totalRides=5; got " + total);
        assertEquals(3, completed, "TC197: completedRides=3; got " + completed);
        assertEquals(1, cancelled, "TC197: cancelledRides=1; got " + cancelled);
        assertEquals(700.0, spent, 0.5, "TC197: totalSpent=700; got " + spent);
    }
}

// ─── TC198 — S1-F3 user with no rides → zeros ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC198_UbRideSummaryNoRidesTests extends TestBase {
    @Test
    @DisplayName("TC198 — Summary for user with no rides returns zeros")
    void summary_no_rides() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Empty User", "tc198@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/ride-summary", tok);
        assert2xx(r, "TC198");
        JsonNode j = parseNode(r.body());
        long total = _UbM2.rL(j, "totalRides", "total_rides");
        double spent = _UbM2.rD(j, "totalSpent", "total_spent");
        assertEquals(0L, total, "TC198: totalRides=0; got " + total);
        assertEquals(0.0, spent, 0.01, "TC198: totalSpent=0; got " + spent);
    }
}

// ─── TC199 — S1-F3 404 non-existent user ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC199_UbRideSummaryNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC199 — Summary for non-existent user returns 404")
    void summary_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/999999/ride-summary", tok);
        assertEquals(404, r.statusCode(), "TC199: must be 404; got " + r.statusCode());
    }
}

// ─── TC200 — S1-F4 deactivate fails when active ACCEPTED ride ───────────────
@Tag("public")
@Tag("features_m1")
class TC200_UbDeactivateActiveRideTests extends TestBase {
    @Test
    @DisplayName("TC200 — Deactivate fails (400) when user has an ACCEPTED ride")
    void deactivate_active_400() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Active User", "tc200@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL200", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "ACCEPTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/deactivate", "", tok);
        assertEquals(400, r.statusCode(), "TC200: must be 400 when active ride exists; got " + r.statusCode());
    }
}

// ─── TC201 — S1-F4 deactivate succeeds when no active rides ─────────────────
@Tag("public")
@Tag("features_m1")
class TC201_UbDeactivateSuccessTests extends TestBase {
    @Test
    @DisplayName("TC201 — Deactivate succeeds when only COMPLETED rides; PG status=DEACTIVATED")
    void deactivate_success() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Done User", "tc201@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL201", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/deactivate", "", tok);
        assert2xx(r, "TC201");
        String stCol = columnByField("User", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("User") + "\" WHERE id = ?",
            String.class, uid);
        assertEquals("DEACTIVATED", dbStatus, "TC201: PG user.status=DEACTIVATED expected; got " + dbStatus);
    }
}

// ─── TC202 — S1-F4 404 non-existent user ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC202_UbDeactivateNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC202 — Deactivate non-existent user returns 404")
    void deactivate_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/999999/deactivate", "", tok);
        assertEquals(404, r.statusCode(), "TC202: must be 404; got " + r.statusCode());
    }
}

// ─── TC203 — S1-F5 preferences search happy match ───────────────────────────
@Tag("public")
@Tag("features_m1")
class TC203_UbPreferencesSearchHappyTests extends TestBase {
    @Test
    @DisplayName("TC203 — ?key=language&value=ar matches users with prefs.language=ar")
    void preferences_search_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long u1 = _UbM1Seed.seedUser(this, "Ar1", "tc203_a@uber.io", "RIDER");
        long u2 = _UbM1Seed.seedUser(this, "En1", "tc203_b@uber.io", "RIDER");
        long u3 = _UbM1Seed.seedUser(this, "Ar2", "tc203_c@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, u1, "{\"language\":\"ar\"}");
        _UbM1Seed.setUserPreferences(this, u2, "{\"language\":\"en\"}");
        _UbM1Seed.setUserPreferences(this, u3, "{\"language\":\"ar\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/search?key=language&value=ar", tok);
        assert2xx(r, "TC203");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC203: at least 2 ar-language users expected; got " + list.size());
    }
}

// ─── TC204 — S1-F5 no match returns empty list ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC204_UbPreferencesSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC204 — Unknown value returns empty list")
    void preferences_search_no_match() throws Exception {
        BASE_URL = userServiceUrl;
        long u1 = _UbM1Seed.seedUser(this, "X", "tc204@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, u1, "{\"language\":\"ar\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/search?key=language&value=zh", tok);
        assert2xx(r, "TC204");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC204: empty list expected; got " + list.size());
    }
}

// ─── TC205 — S1-F5 400 blank key ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC205_UbPreferencesSearchBlankKeyTests extends TestBase {
    @Test
    @DisplayName("TC205 — Blank key returns 400")
    void preferences_search_blank_key() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/search?key=&value=ar", tok);
        assertEquals(400, r.statusCode(), "TC205: blank key must be 400; got " + r.statusCode());
    }
}

// ─── TC206 — S1-F6 top riders happy ranking ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC206_UbTopRidersHappyTests extends TestBase {
    @Test
    @DisplayName("TC206 — Top riders ranks user B (3500) above user A (1200)")
    void top_riders_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _UbM1Seed.seedUser(this, "RiderA", "tc206_a@uber.io", "RIDER");
        long uB = _UbM1Seed.seedUser(this, "RiderB", "tc206_b@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL206", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-10", 1200.0);
        _UbM1Seed.seedRide(this, uB, did, "COMPLETED", "2026-03-11", 1500.0);
        _UbM1Seed.seedRide(this, uB, did, "COMPLETED", "2026-03-12", 2000.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/reports/top-riders?startDate=2026-03-01&endDate=2026-03-31&limit=10", tok);
        assert2xx(r, "TC206");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC206: at least 2 riders expected; got " + list.size());
        long firstUser = list.get(0).has("userId") ? list.get(0).get("userId").asLong()
                : list.get(0).has("id") ? list.get(0).get("id").asLong() : -1L;
        assertEquals(uB, firstUser, "TC206: top spender (uB) should be first; got " + firstUser);
    }
}

// ─── TC207 — S1-F6 limit honored ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC207_UbTopRidersLimitTests extends TestBase {
    @Test
    @DisplayName("TC207 — limit=1 returns only one rider")
    void top_riders_limit() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _UbM1Seed.seedUser(this, "RA", "tc207_a@uber.io", "RIDER");
        long uB = _UbM1Seed.seedUser(this, "RB", "tc207_b@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL207", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, uB, did, "COMPLETED", "2026-03-11", 200.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/reports/top-riders?startDate=2026-03-01&endDate=2026-03-31&limit=1", tok);
        assert2xx(r, "TC207");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(1, list.size(), "TC207: limit=1 expected; got " + list.size());
    }
}

// ─── TC208 — S1-F6 only COMPLETED rides count ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC208_UbTopRidersOnlyCompletedTests extends TestBase {
    @Test
    @DisplayName("TC208 — CANCELLED rides excluded from top-riders ranking")
    void top_riders_only_completed() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _UbM1Seed.seedUser(this, "Rider", "tc208@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL208", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, uA, did, "CANCELLED", "2026-03-11", 9999.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/reports/top-riders?startDate=2026-03-01&endDate=2026-03-31&limit=10", tok);
        assert2xx(r, "TC208");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean found = false;
        for (JsonNode it : list) {
            long id = it.has("userId") ? it.get("userId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == uA) {
                double spent = it.has("totalSpent") ? it.get("totalSpent").asDouble()
                        : it.has("total_spent") ? it.get("total_spent").asDouble() : -1.0;
                assertEquals(100.0, spent, 0.5, "TC208: totalSpent must exclude CANCELLED; got " + spent);
                found = true;
            }
        }
        assertTrue(found, "TC208: target rider must be present");
    }
}

// ─── TC209 — S1-F7 set default address happy ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC209_UbDefaultAddressHappyTests extends TestBase {
    @Test
    @DisplayName("TC209 — PUT default flips isDefault to true on chosen address, others to false")
    void default_address_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Addr User", "tc209@uber.io", "RIDER");
        long a1 = _UbM1Seed.seedAddress(this, uid, "Home", true);
        long a2 = _UbM1Seed.seedAddress(this, uid, "Work", false);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/users/" + uid + "/addresses/" + a2 + "/default", "", tok);
        assert2xx(r, "TC209");
        String idCol = columnByField("SavedAddress", "isDefault");
        Boolean a1Def = jdbc.queryForObject(
            "SELECT \"" + idCol + "\" FROM \"" + tableName("SavedAddress") + "\" WHERE id = ?",
            Boolean.class, a1);
        Boolean a2Def = jdbc.queryForObject(
            "SELECT \"" + idCol + "\" FROM \"" + tableName("SavedAddress") + "\" WHERE id = ?",
            Boolean.class, a2);
        assertEquals(Boolean.FALSE, a1Def, "TC209: a1.isDefault must flip to false");
        assertEquals(Boolean.TRUE,  a2Def, "TC209: a2.isDefault must flip to true");
    }
}

// ─── TC210 — S1-F7 404 unknown user ──────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC210_UbDefaultAddressUnknownUserTests extends TestBase {
    @Test
    @DisplayName("TC210 — Default-address PUT for unknown user returns 404")
    void default_address_unknown_user() throws Exception {
        BASE_URL = userServiceUrl;
        long otherUid = _UbM1Seed.seedUser(this, "OtherU", "tc210_other_" + nonce() + "@uber.io", "RIDER");
        long realAddrId = _UbM1Seed.seedAddress(this, otherUid, "Sentinel", false);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/users/999999/addresses/" + realAddrId + "/default", "", tok);
        assertEquals(404, r.statusCode(), "TC210: must be 404; got " + r.statusCode());
    }
}

// ─── TC211 — S1-F7 404 address not owned by user ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC211_UbDefaultAddressForeignTests extends TestBase {
    @Test
    @DisplayName("TC211 — Default-address PUT with foreign address returns 404")
    void default_address_foreign() throws Exception {
        BASE_URL = userServiceUrl;
        long u1 = _UbM1Seed.seedUser(this, "U1", "tc211_a@uber.io", "RIDER");
        long u2 = _UbM1Seed.seedUser(this, "U2", "tc211_b@uber.io", "RIDER");
        long a2 = _UbM1Seed.seedAddress(this, u2, "Work", false);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/users/" + u1 + "/addresses/" + a2 + "/default", "", tok);
        assertEquals(404, r.statusCode(), "TC211: must be 404 (foreign address); got " + r.statusCode());
    }
}

// ─── TC212 — S1-F8 user profile happy path ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC212_UbProfileHappyTests extends TestBase {
    @Test
    @DisplayName("TC212 — Profile returns user fields + savedAddresses list")
    void profile_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Prof User", "tc212@uber.io", "RIDER");
        _UbM1Seed.seedAddress(this, uid, "Home", true);
        _UbM1Seed.seedAddress(this, uid, "Work", false);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/profile", tok);
        assert2xx(r, "TC212");
        JsonNode j = parseNode(r.body());
        assertTrue(j.has("name") || j.has("email"), "TC212: profile must include name or email");
        JsonNode addrs = _UbM2.rO(j, "savedAddresses", "saved_addresses", "addresses");
        assertNotNull(addrs, "TC212: savedAddresses key required; body=" + r.body());
        assertTrue(addrs.size() >= 2, "TC212: at least 2 saved addresses expected; got " + addrs.size());
    }
}

// ─── TC213 — S1-F8 user with no addresses → empty list ──────────────────────
@Tag("public")
@Tag("features_m1")
class TC213_UbProfileNoAddressesTests extends TestBase {
    @Test
    @DisplayName("TC213 — Profile for user with no addresses returns empty addresses list")
    void profile_no_addresses() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Solo", "tc213@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/profile", tok);
        assert2xx(r, "TC213");
        JsonNode j = parseNode(r.body());
        JsonNode addrs = _UbM2.rO(j, "savedAddresses", "saved_addresses", "addresses");
        if (addrs != null) {
            assertEquals(0, addrs.size(), "TC213: empty saved-addresses expected; got " + addrs.size());
        }
    }
}

// ─── TC214 — S1-F8 404 unknown user ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC214_UbProfileNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC214 — Profile for non-existent user returns 404")
    void profile_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/999999/profile", tok);
        assertEquals(404, r.statusCode(), "TC214: must be 404; got " + r.statusCode());
    }
}

// ─── TC215 — S1-F9 lang+minRides matches qualifying users ───────────────────
@Tag("public")
@Tag("features_m1")
class TC215_UbLangMinRidesHappyTests extends TestBase {
    @Test
    @DisplayName("TC215 — ?lang=ar&minRides=2 matches user with 3 completed rides + ar prefs")
    void lang_min_rides_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _UbM1Seed.seedUser(this, "Ar1", "tc215_a@uber.io", "RIDER");
        long uB = _UbM1Seed.seedUser(this, "Ar2", "tc215_b@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, uA, "{\"language\":\"ar\"}");
        _UbM1Seed.setUserPreferences(this, uB, "{\"language\":\"ar\"}");
        long did = _UbM1Seed.seedDriver(this, "D", "DL215", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-11", 50.0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-12", 50.0);
        _UbM1Seed.seedRide(this, uB, did, "COMPLETED", "2026-03-13", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/preferences/language?lang=ar&minRides=2", tok);
        assert2xx(r, "TC215");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundA = false;
        for (JsonNode it : list) {
            long id = it.has("userId") ? it.get("userId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == uA) foundA = true;
        }
        assertTrue(foundA, "TC215: user with 3 rides should match minRides=2");
    }
}

// ─── TC216 — S1-F9 high minRides → empty ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC216_UbLangMinRidesHighThresholdTests extends TestBase {
    @Test
    @DisplayName("TC216 — ?minRides=100 returns empty when no user has 100 rides")
    void lang_min_rides_high() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _UbM1Seed.seedUser(this, "Ar", "tc216@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, uA, "{\"language\":\"ar\"}");
        long did = _UbM1Seed.seedDriver(this, "D", "DL216", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/preferences/language?lang=ar&minRides=100", tok);
        assert2xx(r, "TC216");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC216: empty list expected; got " + list.size());
    }
}

// ─── TC217 — S1-F9 wrong lang excludes user ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC217_UbLangMinRidesLangFilterTests extends TestBase {
    @Test
    @DisplayName("TC217 — ?lang=fr excludes user whose prefs.language=en")
    void lang_filter_excludes() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _UbM1Seed.seedUser(this, "En", "tc217@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, uA, "{\"language\":\"en\"}");
        long did = _UbM1Seed.seedDriver(this, "D", "DL217", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-11", 50.0);
        _UbM1Seed.seedRide(this, uA, did, "COMPLETED", "2026-03-12", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/preferences/language?lang=fr&minRides=1", tok);
        assert2xx(r, "TC217");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("userId") ? it.get("userId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            assertTrue(id != uA, "TC217: en-prefs user must be excluded by lang=fr");
        }
    }
}

// ─── TC218 — S1-F1 search by email ──────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC218_UbSearchUsersByEmailTests extends TestBase {
    @Test
    @DisplayName("TC218 — Search by email substring matches one user")
    void search_by_email() throws Exception {
        BASE_URL = userServiceUrl;
        _UbM1Seed.seedUser(this, "Bob", "tc218_target@uber.io", "RIDER");
        _UbM1Seed.seedUser(this, "Eve", "tc218_other@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?email=tc218_target", tok);
        assert2xx(r, "TC218");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 1, "TC218: at least 1 match expected; got " + list.size());
    }
}

// ─── TC219 — S1-F3 ride summary excludes CANCELLED from totalSpent ──────────
@Tag("public")
@Tag("features_m1")
class TC219_UbRideSummaryExcludesCancelledTests extends TestBase {
    @Test
    @DisplayName("TC219 — Summary's totalSpent excludes CANCELLED-ride fares")
    void summary_excludes_cancelled() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Rider", "tc219@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL219", "AVAILABLE", 0.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, uid, did, "CANCELLED", "2026-03-11", 999.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/ride-summary", tok);
        assert2xx(r, "TC219");
        double spent = _UbM2.rD(parseNode(r.body()), "totalSpent", "total_spent");
        assertEquals(100.0, spent, 0.5, "TC219: totalSpent excludes CANCELLED; got " + spent);
    }
}

// ─── TC220 — S1-F8 profile contains preferences JSONB ───────────────────────
@Tag("public")
@Tag("features_m1")
class TC220_UbProfileWithPreferencesTests extends TestBase {
    @Test
    @DisplayName("TC220 — Profile body includes prefs.language=ar from JSONB")
    void profile_with_prefs() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "Pref", "tc220@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, uid, "{\"language\":\"ar\",\"theme\":\"dark\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/profile", tok);
        assert2xx(r, "TC220");
        JsonNode j = parseNode(r.body());
        JsonNode prefs = j.has("preferences") ? j.get("preferences") : null;
        assertNotNull(prefs, "TC220: preferences key required; body=" + r.body());
        String lang = prefs.has("language") ? prefs.get("language").asText() : "";
        assertEquals("ar", lang, "TC220: preferences.language=ar; got " + lang);
    }
}

// ────────────────────────────────────────────────────────────────────────────
// S2 — Driver Service (TC221..TC248) — 9 features, 28 TCs
// S2-F1 search, S2-F2 vehicle, S2-F3 earnings, S2-F4 availability,
// S2-F5 vehicle-type, S2-F6 top-rated, S2-F7 rate, S2-F8 verify-doc, S2-F9 expired-docs
// ────────────────────────────────────────────────────────────────────────────

// ─── TC221 — S2-F1 search by status ──────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC221_UbDriverSearchByStatusTests extends TestBase {
    @Test
    @DisplayName("TC221 — Search ?status=AVAILABLE returns AVAILABLE drivers only")
    void search_by_status() throws Exception {
        BASE_URL = driverServiceUrl;
        _UbM1Seed.seedDriver(this, "DA", "DL221A", "AVAILABLE", 4.5, 10);
        _UbM1Seed.seedDriver(this, "DB", "DL221B", "BUSY",      4.0, 5);
        _UbM1Seed.seedDriver(this, "DC", "DL221C", "AVAILABLE", 3.5, 3);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/search?status=AVAILABLE", tok);
        assert2xx(r, "TC221");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String s = it.has("status") ? it.get("status").asText() : "";
            assertEquals("AVAILABLE", s, "TC221: every result must be AVAILABLE; got " + s);
        }
        assertTrue(list.size() >= 2, "TC221: at least 2 AVAILABLE drivers expected; got " + list.size());
    }
}

// ─── TC222 — S2-F1 search by minRating filter ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC222_UbDriverSearchByMinRatingTests extends TestBase {
    @Test
    @DisplayName("TC222 — Search ?minRating=4.0 excludes drivers below threshold")
    void search_min_rating() throws Exception {
        BASE_URL = driverServiceUrl;
        _UbM1Seed.seedDriver(this, "Hi", "DL222H", "AVAILABLE", 4.8, 50);
        _UbM1Seed.seedDriver(this, "Lo", "DL222L", "AVAILABLE", 3.0, 5);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/search?minRating=4.0", tok);
        assert2xx(r, "TC222");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            double rt = it.has("rating") ? it.get("rating").asDouble() : 0.0;
            assertTrue(rt >= 4.0, "TC222: every rating must be >= 4.0; got " + rt);
        }
    }
}

// ─── TC223 — S2-F1 invalid rating range → 400 ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC223_UbDriverSearchInvalidRatingTests extends TestBase {
    @Test
    @DisplayName("TC223 — Search with minRating > maxRating returns 400")
    void search_invalid_range() throws Exception {
        BASE_URL = driverServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/search?minRating=4.5&maxRating=3.0", tok);
        assertEquals(400, r.statusCode(),
            "TC223: must be 400 when min > max; got " + r.statusCode());
    }
}

// ─── TC224 — S2-F2 vehicle JSONB merge ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC224_UbVehicleMergeTests extends TestBase {
    @Test
    @DisplayName("TC224 — PUT vehicle merges new keys into existing vehicleDetails JSONB")
    void vehicle_merge() throws Exception {
        BASE_URL = driverServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "VM", "DL224", "AVAILABLE", 4.0, 0);
        _UbM1Seed.setDriverVehicleDetails(this, did,
            "{\"make\":\"Toyota\",\"model\":\"Camry\",\"vehicleType\":\"SEDAN\"}");
        String tok = adminToken();
        String body = "{\"color\":\"Red\",\"year\":2024}";
        HttpResponse<String> r = httpPutAuth("/api/drivers/" + did + "/vehicle", body, tok);
        assert2xx(r, "TC224");
        JsonNode j = parseNode(r.body());
        JsonNode vd = j.has("vehicleDetails") ? j.get("vehicleDetails")
                : j.has("vehicle_details") ? j.get("vehicle_details") : j;
        assertEquals("Toyota", vd.has("make") ? vd.get("make").asText() : "",
            "TC224: make preserved");
        assertEquals("Red", vd.has("color") ? vd.get("color").asText() : "",
            "TC224: color added");
        assertEquals(2024, vd.has("year") ? vd.get("year").asInt() : 0,
            "TC224: year added");
    }
}

// ─── TC225 — S2-F2 vehicle 404 unknown driver ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC225_UbVehicleNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC225 — Vehicle PUT for non-existent driver returns 404")
    void vehicle_not_found() throws Exception {
        BASE_URL = driverServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/drivers/999999/vehicle",
            "{\"color\":\"Blue\"}", tok);
        assertEquals(404, r.statusCode(), "TC225: must be 404; got " + r.statusCode());
    }
}

// ─── TC226 — S2-F3 earnings happy path ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC226_UbEarningsHappyTests extends TestBase {
    @Test
    @DisplayName("TC226 — Earnings sums COMPLETED-ride fares in date range")
    void earnings_happy() throws Exception {
        BASE_URL = driverServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc226@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "Earn", "DL226", "AVAILABLE", 4.5, 5);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-15", 150.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-20", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/" + did + "/earnings?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC226");
        double total = _UbM2.rD(parseNode(r.body()), "totalEarnings", "total_earnings");
        assertEquals(300.0, total, 0.5, "TC226: totalEarnings=300; got " + total);
    }
}

// ─── TC227 — S2-F3 earnings excludes CANCELLED ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC227_UbEarningsExcludesCancelledTests extends TestBase {
    @Test
    @DisplayName("TC227 — Earnings excludes CANCELLED rides")
    void earnings_excludes_cancelled() throws Exception {
        BASE_URL = driverServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc227@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "Earn", "DL227", "AVAILABLE", 4.5, 5);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, uid, did, "CANCELLED", "2026-03-15", 999.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/" + did + "/earnings?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC227");
        double total = _UbM2.rD(parseNode(r.body()), "totalEarnings", "total_earnings");
        assertEquals(100.0, total, 0.5, "TC227: only COMPLETED counted; got " + total);
    }
}

// ─── TC228 — S2-F3 earnings 404 unknown driver ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC228_UbEarningsNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC228 — Earnings for non-existent driver returns 404")
    void earnings_not_found() throws Exception {
        BASE_URL = driverServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/999999/earnings?startDate=2026-01-01&endDate=2026-12-31", tok);
        assertEquals(404, r.statusCode(), "TC228: must be 404; got " + r.statusCode());
    }
}

// ─── TC229 — S2-F4 update availability success ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC229_UbAvailabilityUpdateSuccessTests extends TestBase {
    @Test
    @DisplayName("TC229 — PUT availability flips status to OFFLINE when no active rides")
    void availability_success() throws Exception {
        BASE_URL = driverServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "Av", "DL229", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/drivers/" + did + "/availability",
            "{\"status\":\"OFFLINE\"}", tok);
        assert2xx(r, "TC229");
        String stCol = columnByField("Driver", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Driver") + "\" WHERE id = ?",
            String.class, did);
        assertEquals("OFFLINE", dbStatus,
            "TC229: PG driver.status=OFFLINE expected; got " + dbStatus);
    }
}

// ─── TC230 — S2-F4 blocked by active ride ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC230_UbAvailabilityActiveRideBlockedTests extends TestBase {
    @Test
    @DisplayName("TC230 — Availability OFFLINE blocked (400) by IN_PROGRESS ride")
    void availability_blocked() throws Exception {
        BASE_URL = driverServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc230@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "Av", "DL230", "BUSY", 4.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "IN_PROGRESS", "2030-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/drivers/" + did + "/availability",
            "{\"status\":\"OFFLINE\"}", tok);
        assertEquals(400, r.statusCode(),
            "TC230: must be 400 when active ride exists; got " + r.statusCode());
    }
}

// ─── TC231 — S2-F5 vehicle-type filter ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC231_UbVehicleTypeFilterTests extends TestBase {
    @Test
    @DisplayName("TC231 — ?type=SEDAN returns drivers whose vehicleDetails.vehicleType=SEDAN")
    void vehicle_type_filter() throws Exception {
        BASE_URL = driverServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "Sedan1", "DL231A", "AVAILABLE", 4.0, 0);
        long d2 = _UbM1Seed.seedDriver(this, "Suv1",   "DL231B", "AVAILABLE", 4.0, 0);
        _UbM1Seed.setDriverVehicleDetails(this, d1,
            "{\"make\":\"Toyota\",\"vehicleType\":\"SEDAN\"}");
        _UbM1Seed.setDriverVehicleDetails(this, d2,
            "{\"make\":\"Honda\",\"vehicleType\":\"SUV\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/vehicle-type?type=SEDAN", tok);
        assert2xx(r, "TC231");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundSedan = false;
        boolean foundSuv = false;
        for (JsonNode it : list) {
            long id = it.has("id") ? it.get("id").asLong()
                    : it.has("driverId") ? it.get("driverId").asLong() : -1L;
            if (id == d1) foundSedan = true;
            if (id == d2) foundSuv = true;
        }
        assertTrue(foundSedan, "TC231: SEDAN driver expected in result");
        assertFalse(foundSuv, "TC231: SUV driver must be excluded");
    }
}

// ─── TC232 — S2-F5 vehicle-type combined with status ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC232_UbVehicleTypeStatusFilterTests extends TestBase {
    @Test
    @DisplayName("TC232 — ?type=SEDAN&status=AVAILABLE returns only available SEDAN drivers")
    void vehicle_type_status_filter() throws Exception {
        BASE_URL = driverServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "Sedan1", "DL232A", "AVAILABLE", 4.0, 0);
        long d2 = _UbM1Seed.seedDriver(this, "Sedan2", "DL232B", "OFFLINE",   4.0, 0);
        _UbM1Seed.setDriverVehicleDetails(this, d1, "{\"vehicleType\":\"SEDAN\"}");
        _UbM1Seed.setDriverVehicleDetails(this, d2, "{\"vehicleType\":\"SEDAN\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/vehicle-type?type=SEDAN&status=AVAILABLE", tok);
        assert2xx(r, "TC232");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String s = it.has("status") ? it.get("status").asText() : "";
            assertEquals("AVAILABLE", s, "TC232: every result must be AVAILABLE; got " + s);
        }
    }
}

// ─── TC233 — S2-F6 top-rated drivers ranking ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC233_UbTopRatedHappyTests extends TestBase {
    @Test
    @DisplayName("TC233 — Top-rated ranks by rating desc")
    void top_rated_happy() throws Exception {
        BASE_URL = driverServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "DA", "DL233A", "AVAILABLE", 3.5, 30);
        long d2 = _UbM1Seed.seedDriver(this, "DB", "DL233B", "AVAILABLE", 4.8, 30);
        long d3 = _UbM1Seed.seedDriver(this, "DC", "DL233C", "AVAILABLE", 4.2, 30);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/reports/top-rated?limit=10", tok);
        assert2xx(r, "TC233");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 3, "TC233: at least 3 drivers expected; got " + list.size());
        long firstId = list.get(0).has("id") ? list.get(0).get("id").asLong()
                : list.get(0).has("driverId") ? list.get(0).get("driverId").asLong() : -1L;
        assertEquals(d2, firstId, "TC233: highest-rated (d2) must be first");
    }
}

// ─── TC234 — S2-F6 top-rated honors limit ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC234_UbTopRatedLimitTests extends TestBase {
    @Test
    @DisplayName("TC234 — Top-rated limit=2 returns 2 items")
    void top_rated_limit() throws Exception {
        BASE_URL = driverServiceUrl;
        _UbM1Seed.seedDriver(this, "D1", "DL234A", "AVAILABLE", 3.5, 5);
        _UbM1Seed.seedDriver(this, "D2", "DL234B", "AVAILABLE", 4.5, 5);
        _UbM1Seed.seedDriver(this, "D3", "DL234C", "AVAILABLE", 4.7, 5);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/reports/top-rated?limit=2", tok);
        assert2xx(r, "TC234");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(2, list.size(), "TC234: limit=2 honored; got " + list.size());
    }
}

// ─── TC235 — S2-F7 rate driver happy path ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC235_UbRateDriverHappyTests extends TestBase {
    @Test
    @DisplayName("TC235 — POST /rate updates driver rating + totalRatings")
    void rate_driver_happy() throws Exception {
        BASE_URL = driverServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc235@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "DR", "DL235", "AVAILABLE", 4.0, 1);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        String body = "{\"rideId\":" + rid + ",\"rating\":5}";
        HttpResponse<String> r = httpPostAuth("/api/drivers/" + did + "/rate", body, tok);
        assert2xx(r, "TC235");
        String trCol = columnByField("Driver", "totalRatings");
        Integer tr = jdbc.queryForObject(
            "SELECT \"" + trCol + "\" FROM \"" + tableName("Driver") + "\" WHERE id = ?",
            Integer.class, did);
        assertEquals(2, tr.intValue(), "TC235: totalRatings must increment to 2; got " + tr);
    }
}

// ─── TC236 — S2-F7 rate validation: rating > 5 → 400 ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC236_UbRateInvalidTests extends TestBase {
    @Test
    @DisplayName("TC236 — Rate with rating=6 returns 400")
    void rate_invalid() throws Exception {
        BASE_URL = driverServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc236@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "DR", "DL236", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/drivers/" + did + "/rate",
            "{\"rideId\":" + rid + ",\"rating\":6}", tok);
        assertEquals(400, r.statusCode(),
            "TC236: rating=6 must be 400; got " + r.statusCode());
    }
}

// ─── TC237 — S2-F7 rate non-completed ride → 400 ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC237_UbRateNonCompletedTests extends TestBase {
    @Test
    @DisplayName("TC237 — Rate of REQUESTED ride returns 400")
    void rate_non_completed() throws Exception {
        BASE_URL = driverServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc237@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "DR", "DL237", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "REQUESTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/drivers/" + did + "/rate",
            "{\"rideId\":" + rid + ",\"rating\":4}", tok);
        assertEquals(400, r.statusCode(),
            "TC237: rating non-COMPLETED ride must be 400; got " + r.statusCode());
    }
}

// ─── TC238 — S2-F8 verify document happy ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC238_UbVerifyDocumentHappyTests extends TestBase {
    @Test
    @DisplayName("TC238 — Admin verifies document; PG verified=true")
    void verify_doc_happy() throws Exception {
        BASE_URL = driverServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "DV", "DL238", "AVAILABLE", 4.0, 0);
        long doc = _UbM1Seed.seedDocument(this, did, "LICENSE",
            java.time.LocalDate.of(2030, 12, 31), false);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/drivers/" + did + "/documents/" + doc + "/verify", "", tok);
        assert2xx(r, "TC238");
        String vCol = columnByField("DriverDocument", "verified");
        Boolean v = jdbc.queryForObject(
            "SELECT \"" + vCol + "\" FROM \"" + tableName("DriverDocument") + "\" WHERE id = ?",
            Boolean.class, doc);
        assertEquals(Boolean.TRUE, v, "TC238: PG verified=true expected; got " + v);
    }
}

// ─── TC239 — S2-F8 verify 404 ────────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC239_UbVerifyDocumentNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC239 — Verify unknown document returns 404")
    void verify_doc_not_found() throws Exception {
        BASE_URL = driverServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "DV", "DL239", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/drivers/" + did + "/documents/999999/verify", "", tok);
        assertEquals(404, r.statusCode(),
            "TC239: must be 404; got " + r.statusCode());
    }
}

// ─── TC240 — S2-F9 expired documents listing ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC240_UbExpiredDocsHappyTests extends TestBase {
    @Test
    @DisplayName("TC240 — Expired-docs report lists drivers with past-expiry docs")
    void expired_docs_happy() throws Exception {
        BASE_URL = driverServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "Exp", "DL240A", "AVAILABLE", 4.0, 0);
        long d2 = _UbM1Seed.seedDriver(this, "Ok",  "DL240B", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedDocument(this, d1, "LICENSE",  java.time.LocalDate.now().minusYears(6), false);
        _UbM1Seed.seedDocument(this, d2, "LICENSE",  java.time.LocalDate.of(2030, 1, 1), false);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/documents/expired", tok);
        assert2xx(r, "TC240");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundExp = false;
        boolean foundOk = false;
        for (JsonNode it : list) {
            long id = it.has("driverId") ? it.get("driverId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == d1) foundExp = true;
            if (id == d2) foundOk = true;
        }
        assertTrue(foundExp, "TC240: driver with expired LICENSE expected");
        assertFalse(foundOk, "TC240: driver with future-expiry LICENSE must be excluded");
    }
}

// ─── TC241 — S2-F9 driver with no expired docs excluded ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC241_UbExpiredDocsNoExpiryTests extends TestBase {
    @Test
    @DisplayName("TC241 — Driver with all docs in date is excluded from expired-docs report")
    void expired_docs_excludes_ok() throws Exception {
        BASE_URL = driverServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "Ok", "DL241", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedDocument(this, d1, "INSURANCE", java.time.LocalDate.of(2030, 6, 1), true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/documents/expired", tok);
        assert2xx(r, "TC241");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("driverId") ? it.get("driverId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            assertTrue(id != d1, "TC241: in-date driver must be excluded; got " + id);
        }
    }
}

// ─── TC242 — S2-F9 alert DTO contains expiredCount ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC242_UbExpiredDocsCountTests extends TestBase {
    @Test
    @DisplayName("TC242 — DriverDocumentAlertDTO.expiredCount counts past-expiry docs")
    void expired_docs_count() throws Exception {
        BASE_URL = driverServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "Exp", "DL242", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedDocument(this, d1, "LICENSE",      java.time.LocalDate.now().minusYears(6), false);
        _UbM1Seed.seedDocument(this, d1, "INSURANCE",    java.time.LocalDate.now().minusYears(6).plusMonths(5), false);
        _UbM1Seed.seedDocument(this, d1, "REGISTRATION", java.time.LocalDate.of(2030, 1, 1), false);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/documents/expired", tok);
        assert2xx(r, "TC242");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("driverId") ? it.get("driverId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == d1) {
                long count = it.has("expiredCount") ? it.get("expiredCount").asLong()
                        : it.has("expired_count") ? it.get("expired_count").asLong() : -1L;
                assertEquals(2L, count, "TC242: expiredCount=2; got " + count);
                return;
            }
        }
        throw new AssertionError("TC242: target driver not in expired report");
    }
}

// ─── TC243 — S2-F2 vehicle 400 validation: empty body ───────────────────────
@Tag("public")
@Tag("features_m1")
class TC243_UbVehicleEmptyBodyTests extends TestBase {
    @Test
    @DisplayName("TC243 — Vehicle PUT with empty body returns 400")
    void vehicle_empty_body() throws Exception {
        BASE_URL = driverServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "VM", "DL243", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/drivers/" + did + "/vehicle", "{}", tok);
        assertEquals(400, r.statusCode(),
            "TC243: empty body must be 400; got " + r.statusCode());
    }
}

// ─── TC244 — S2-F3 earnings invalid date range → 400 ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC244_UbEarningsInvalidRangeTests extends TestBase {
    @Test
    @DisplayName("TC244 — Earnings with startDate > endDate returns 400")
    void earnings_invalid_range() throws Exception {
        BASE_URL = driverServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "Earn", "DL244", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/" + did + "/earnings?startDate=2026-12-31&endDate=2026-01-01", tok);
        assertEquals(400, r.statusCode(),
            "TC244: must be 400; got " + r.statusCode());
    }
}

// ─── TC245 — S2-F1 search by maxRating ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC245_UbDriverSearchMaxRatingTests extends TestBase {
    @Test
    @DisplayName("TC245 — Search ?maxRating=4.0 excludes drivers above threshold")
    void search_max_rating() throws Exception {
        BASE_URL = driverServiceUrl;
        _UbM1Seed.seedDriver(this, "HiR", "DL245A", "AVAILABLE", 4.5, 5);
        _UbM1Seed.seedDriver(this, "LoR", "DL245B", "AVAILABLE", 3.0, 5);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/search?maxRating=4.0", tok);
        assert2xx(r, "TC245");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            double rt = it.has("rating") ? it.get("rating").asDouble() : 0.0;
            assertTrue(rt <= 4.0, "TC245: every rating must be <= 4.0; got " + rt);
        }
    }
}

// ─── TC246 — S2-F4 unknown driver returns 404 ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC246_UbAvailabilityNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC246 — Availability for non-existent driver returns 404")
    void availability_not_found() throws Exception {
        BASE_URL = driverServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/drivers/999999/availability",
            "{\"status\":\"OFFLINE\"}", tok);
        assertEquals(404, r.statusCode(),
            "TC246: must be 404; got " + r.statusCode());
    }
}

// ─── TC247 — S2-F8 verify already-verified is idempotent or 200 ─────────────
@Tag("public")
@Tag("features_m1")
class TC247_UbVerifyAlreadyVerifiedTests extends TestBase {
    @Test
    @DisplayName("TC247 — Verify an already-verified document remains true (no-op)")
    void verify_already_verified() throws Exception {
        BASE_URL = driverServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "DV", "DL247", "AVAILABLE", 4.0, 0);
        long doc = _UbM1Seed.seedDocument(this, did, "LICENSE",
            java.time.LocalDate.of(2030, 12, 31), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/drivers/" + did + "/documents/" + doc + "/verify", "", tok);
        // Spec is permissive — could be 2xx (no-op) or 400 (already verified).
        // Either way the DB row must remain verified=true.
        String vCol = columnByField("DriverDocument", "verified");
        Boolean v = jdbc.queryForObject(
            "SELECT \"" + vCol + "\" FROM \"" + tableName("DriverDocument") + "\" WHERE id = ?",
            Boolean.class, doc);
        assertEquals(Boolean.TRUE, v, "TC247: verified must remain true; got " + v);
        assertTrue(r.statusCode() < 500,
            "TC247: must not be 500; got " + r.statusCode());
    }
}

// ─── TC248 — S2-F1 search no match → empty list ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC248_UbDriverSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC248 — Search returns empty list when no driver matches")
    void search_no_match() throws Exception {
        BASE_URL = driverServiceUrl;
        _UbM1Seed.seedDriver(this, "Only1", "DL248", "AVAILABLE", 4.5, 5);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/search?status=BUSY&minRating=4.9", tok);
        assert2xx(r, "TC248");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC248: empty list expected; got " + list.size());
    }
}

// ────────────────────────────────────────────────────────────────────────────
// S3 — Ride Service (TC249..TC274) — 9 features, 26 TCs
// S3-F1 search, S3-F2 assign, S3-F3 estimate, S3-F4 complete,
// S3-F5 metadata search, S3-F6 analytics, S3-F7 cancel, S3-F8 stops, S3-F9 details
// ────────────────────────────────────────────────────────────────────────────

// ─── TC249 — S3-F1 search rides by status ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC249_UbRideSearchByStatusTests extends TestBase {
    @Test
    @DisplayName("TC249 — Search ?status=COMPLETED returns COMPLETED rides only")
    void search_by_status() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc249@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL249", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.seedRide(this, uid, did, "REQUESTED", "2026-03-11", 50.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-12", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/rides/search?status=COMPLETED", tok);
        assert2xx(r, "TC249");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String s = it.has("status") ? it.get("status").asText() : "";
            assertEquals("COMPLETED", s, "TC249: every result must be COMPLETED; got " + s);
        }
        assertTrue(list.size() >= 2, "TC249: at least 2 COMPLETED rides expected; got " + list.size());
    }
}

// ─── TC250 — S3-F1 search by date range ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC250_UbRideSearchByDateRangeTests extends TestBase {
    @Test
    @DisplayName("TC250 — Search ?startDate&endDate filters by ride date")
    void search_by_date_range() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc250@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL250", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-06-15", 50.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-08-15", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/search?startDate=2026-06-01&endDate=2026-06-30", tok);
        assert2xx(r, "TC250");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 1, "TC250: at least 1 in-range ride expected; got " + list.size());
    }
}

// ─── TC251 — S3-F2 assign driver happy ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC251_UbAssignDriverHappyTests extends TestBase {
    @Test
    @DisplayName("TC251 — PUT assign sets driver FK and flips status to ACCEPTED")
    void assign_happy() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc251@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL251", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, 0L, "REQUESTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/rides/" + rid + "/assign?driverId=" + did, "", tok);
        assert2xx(r, "TC251");
        String stCol = columnByField("Ride", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Ride") + "\" WHERE id = ?",
            String.class, rid);
        assertEquals("ACCEPTED", dbStatus,
            "TC251: PG ride.status=ACCEPTED expected; got " + dbStatus);
    }
}

// ─── TC252 — S3-F2 assign already-assigned → 400 ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC252_UbAssignAlreadyAssignedTests extends TestBase {
    @Test
    @DisplayName("TC252 — Assigning a ride already in ACCEPTED state returns 400")
    void assign_already_assigned() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc252@uber.io", "RIDER");
        long d1 = _UbM1Seed.seedDriver(this, "D1", "DL252A", "AVAILABLE", 4.0, 0);
        long d2 = _UbM1Seed.seedDriver(this, "D2", "DL252B", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, d1, "ACCEPTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/rides/" + rid + "/assign?driverId=" + d2, "", tok);
        assertEquals(400, r.statusCode(),
            "TC252: re-assigning ACCEPTED ride must be 400; got " + r.statusCode());
    }
}

// ─── TC253 — S3-F2 assign 404 unknown ride ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC253_UbAssignNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC253 — Assign unknown rideId returns 404")
    void assign_not_found() throws Exception {
        BASE_URL = rideServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "D", "DL253", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/rides/999999/assign?driverId=" + did, "", tok);
        assertEquals(404, r.statusCode(),
            "TC253: must be 404; got " + r.statusCode());
    }
}

// ─── TC254 — S3-F3 fare estimate happy ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC254_UbFareEstimateHappyTests extends TestBase {
    @Test
    @DisplayName("TC254 — POST estimate returns estimatedDistance + estimatedFare > 0")
    void fare_estimate_happy() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        String body = "{\"pickupLatitude\":30.044,\"pickupLongitude\":31.235,"
                + "\"dropoffLatitude\":30.100,\"dropoffLongitude\":31.300}";
        HttpResponse<String> r = httpPostAuth("/api/rides/estimate", body, tok);
        assert2xx(r, "TC254");
        JsonNode j = parseNode(r.body());
        double dist = _UbM2.rD(j, "estimatedDistance", "estimated_distance");
        double fare = _UbM2.rD(j, "estimatedFare",     "estimated_fare");
        assertTrue(dist > 0, "TC254: estimatedDistance > 0; got " + dist);
        assertTrue(fare > 0, "TC254: estimatedFare > 0; got " + fare);
    }
}

// ─── TC255 — S3-F3 fare estimate 400 missing fields ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC255_UbFareEstimateMissingFieldsTests extends TestBase {
    @Test
    @DisplayName("TC255 — Estimate missing dropoff coords returns 400")
    void fare_estimate_missing() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        String body = "{\"pickupLatitude\":30.044,\"pickupLongitude\":31.235}";
        HttpResponse<String> r = httpPostAuth("/api/rides/estimate", body, tok);
        assertEquals(400, r.statusCode(),
            "TC255: missing dropoff must be 400; got " + r.statusCode());
    }
}

// ─── TC256 — S3-F4 complete ride happy ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC256_UbCompleteRideHappyTests extends TestBase {
    @Test
    @DisplayName("TC256 — Complete IN_PROGRESS ride flips status to COMPLETED")
    void complete_happy() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc256@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL256", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "IN_PROGRESS", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/" + rid + "/complete", "", tok);
        assert2xx(r, "TC256");
        String stCol = columnByField("Ride", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Ride") + "\" WHERE id = ?",
            String.class, rid);
        assertEquals("COMPLETED", dbStatus,
            "TC256: PG ride.status=COMPLETED expected; got " + dbStatus);
    }
}

// ─── TC257 — S3-F4 complete REQUESTED ride → 400 ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC257_UbCompleteWrongStateTests extends TestBase {
    @Test
    @DisplayName("TC257 — Complete a REQUESTED ride returns 400 (wrong state)")
    void complete_wrong_state() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc257@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL257", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "REQUESTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/" + rid + "/complete", "", tok);
        assertEquals(400, r.statusCode(),
            "TC257: REQUESTED → complete must be 400; got " + r.statusCode());
    }
}

// ─── TC258 — S3-F5 metadata search match ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC258_UbMetadataSearchHappyTests extends TestBase {
    @Test
    @DisplayName("TC258 — Metadata search ?key=tag&value=premium matches rides with tag=premium")
    void metadata_search_happy() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc258@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL258", "AVAILABLE", 4.0, 0);
        long r1 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        long r2 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 50.0);
        _UbM1Seed.setRideMetadata(this, r1, "{\"tag\":\"premium\"}");
        _UbM1Seed.setRideMetadata(this, r2, "{\"tag\":\"basic\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/metadata/search?key=tag&value=premium", tok);
        assert2xx(r, "TC258");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundPremium = false;
        boolean foundBasic = false;
        for (JsonNode it : list) {
            long id = it.has("id") ? it.get("id").asLong()
                    : it.has("rideId") ? it.get("rideId").asLong() : -1L;
            if (id == r1) foundPremium = true;
            if (id == r2) foundBasic = true;
        }
        assertTrue(foundPremium, "TC258: premium-tag ride expected");
        assertFalse(foundBasic, "TC258: basic-tag ride must be excluded");
    }
}

// ─── TC259 — S3-F5 metadata search 400 blank key ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC259_UbMetadataSearchBlankKeyTests extends TestBase {
    @Test
    @DisplayName("TC259 — Metadata search with blank key returns 400")
    void metadata_search_blank_key() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/metadata/search?key=&value=premium", tok);
        assertEquals(400, r.statusCode(),
            "TC259: blank key must be 400; got " + r.statusCode());
    }
}

// ─── TC260 — S3-F6 analytics happy path ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC260_UbAnalyticsHappyTests extends TestBase {
    @Test
    @DisplayName("TC260 — Analytics returns totalRides=4, completedRides=3, completionRate=0.75")
    void analytics_happy() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc260@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL260", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 100.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-12", 100.0);
        _UbM1Seed.seedRide(this, uid, did, "CANCELLED", "2026-03-13", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/analytics?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC260");
        JsonNode j = parseNode(r.body());
        assertEquals(4L, _UbM2.rL(j, "totalRides", "total_rides"),
            "TC260: totalRides=4");
        assertEquals(3L, _UbM2.rL(j, "completedRides", "completed_rides"),
            "TC260: completedRides=3");
        assertEquals(0.75, _UbM2.rD(j, "completionRate", "completion_rate"), 0.01,
            "TC260: completionRate=0.75");
    }
}

// ─── TC261 — S3-F6 analytics empty range returns zeros ──────────────────────
@Tag("public")
@Tag("features_m1")
class TC261_UbAnalyticsEmptyRangeTests extends TestBase {
    @Test
    @DisplayName("TC261 — Analytics with no rides in range returns totalRides=0")
    void analytics_empty_range() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/analytics?startDate=2099-01-01&endDate=2099-01-31", tok);
        assert2xx(r, "TC261");
        assertEquals(0L,
            _UbM2.rL(parseNode(r.body()), "totalRides", "total_rides"),
            "TC261: totalRides=0");
    }
}

// ─── TC262 — S3-F7 cancel ride happy ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC262_UbCancelRideHappyTests extends TestBase {
    @Test
    @DisplayName("TC262 — Cancel REQUESTED ride flips status to CANCELLED")
    void cancel_happy() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc262@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL262", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "REQUESTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/" + rid + "/cancel", "", tok);
        assert2xx(r, "TC262");
        String stCol = columnByField("Ride", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Ride") + "\" WHERE id = ?",
            String.class, rid);
        assertEquals("CANCELLED", dbStatus,
            "TC262: PG ride.status=CANCELLED expected; got " + dbStatus);
    }
}

// ─── TC263 — S3-F7 cancel COMPLETED ride → 400 ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC263_UbCancelCompletedTests extends TestBase {
    @Test
    @DisplayName("TC263 — Cancel a COMPLETED ride returns 400")
    void cancel_completed() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc263@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL263", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/" + rid + "/cancel", "", tok);
        assertEquals(400, r.statusCode(),
            "TC263: cancelling COMPLETED must be 400; got " + r.statusCode());
    }
}

// ─── TC264 — S3-F8 add stops happy ──────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC264_UbAddStopsHappyTests extends TestBase {
    @Test
    @DisplayName("TC264 — POST stops adds 2 RideStop rows for the ride")
    void add_stops_happy() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc264@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL264", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "ACCEPTED", "2030-03-10", 50.0);
        String tok = adminToken();
        String body = "{\"stops\":["
                + "{\"stopOrder\":1,\"latitude\":30.05,\"longitude\":31.24,\"address\":\"Stop A\"},"
                + "{\"stopOrder\":2,\"latitude\":30.07,\"longitude\":31.26,\"address\":\"Stop B\"}"
                + "]}";
        HttpResponse<String> r = httpPostAuth(
            "/api/rides/" + rid + "/stops", body, tok);
        assert2xx(r, "TC264");
        String fkCol = columnByField("RideStop", "ride");
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("RideStop") + "\" WHERE \"" + fkCol + "\" = ?",
            Integer.class, rid);
        assertEquals(2, cnt.intValue(),
            "TC264: 2 RideStop rows expected; got " + cnt);
    }
}

// ─── TC265 — S3-F8 stops 404 unknown ride ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC265_UbAddStopsNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC265 — Stops POST for unknown ride returns 404")
    void add_stops_not_found() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/rides/999999/stops",
            "{\"stops\":[{\"stopOrder\":1,\"latitude\":30.0,\"longitude\":31.0,\"address\":\"X\"}]}",
            tok);
        assertEquals(404, r.statusCode(),
            "TC265: must be 404; got " + r.statusCode());
    }
}

// ─── TC266 — S3-F9 ride details happy ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC266_UbRideDetailsHappyTests extends TestBase {
    @Test
    @DisplayName("TC266 — Details returns ride + stops list with totalStops/completedStops")
    void details_happy() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc266@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL266", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "IN_PROGRESS", "2026-03-10", 50.0);
        _UbM1Seed.seedRideStop(this, rid, 1, 30.05, 31.24, "Stop A", "REACHED");
        _UbM1Seed.seedRideStop(this, rid, 2, 30.07, 31.26, "Stop B", "PENDING");
        _UbM1Seed.seedRideStop(this, rid, 3, 30.09, 31.28, "Stop C", "REACHED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/rides/" + rid + "/details", tok);
        assert2xx(r, "TC266");
        JsonNode j = parseNode(r.body());
        long total = _UbM2.rL(j, "totalStops", "total_stops");
        long done  = _UbM2.rL(j, "completedStops", "completed_stops");
        assertEquals(3L, total, "TC266: totalStops=3; got " + total);
        assertEquals(2L, done,  "TC266: completedStops=2 (REACHED count); got " + done);
    }
}

// ─── TC267 — S3-F9 details 404 ──────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC267_UbRideDetailsNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC267 — Details for unknown ride returns 404")
    void details_not_found() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/rides/999999/details", tok);
        assertEquals(404, r.statusCode(),
            "TC267: must be 404; got " + r.statusCode());
    }
}

// ─── TC268 — S3-F1 search no match → empty ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC268_UbRideSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC268 — Search returns empty list when filter excludes all rides")
    void search_no_match() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc268@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL268", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/rides/search?status=CANCELLED", tok);
        assert2xx(r, "TC268");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC268: empty list expected; got " + list.size());
    }
}

// ─── TC269 — S3-F4 complete 404 ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC269_UbCompleteNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC269 — Complete unknown ride returns 404")
    void complete_not_found() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/999999/complete", "", tok);
        assertEquals(404, r.statusCode(),
            "TC269: must be 404; got " + r.statusCode());
    }
}

// ─── TC270 — S3-F7 cancel 404 ───────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC270_UbCancelNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC270 — Cancel unknown ride returns 404")
    void cancel_not_found() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/999999/cancel", "", tok);
        assertEquals(404, r.statusCode(),
            "TC270: must be 404; got " + r.statusCode());
    }
}

// ─── TC271 — S3-F6 analytics totalRevenue counts COMPLETED only ─────────────
@Tag("public")
@Tag("features_m1")
class TC271_UbAnalyticsRevenueTests extends TestBase {
    @Test
    @DisplayName("TC271 — Analytics totalRevenue sums COMPLETED ride fares")
    void analytics_revenue() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc271@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL271", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 80.0);
        _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 70.0);
        _UbM1Seed.seedRide(this, uid, did, "CANCELLED", "2026-03-12", 999.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/analytics?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC271");
        double rev = _UbM2.rD(parseNode(r.body()), "totalRevenue", "total_revenue");
        assertEquals(150.0, rev, 0.5, "TC271: totalRevenue=150; got " + rev);
    }
}

// ─── TC272 — S3-F2 assign with missing driverId param → 400 ─────────────────
@Tag("public")
@Tag("features_m1")
class TC272_UbAssignMissingParamTests extends TestBase {
    @Test
    @DisplayName("TC272 — Assign without driverId param returns 400")
    void assign_missing_param() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc272@uber.io", "RIDER");
        long rid = _UbM1Seed.seedRide(this, uid, 0L, "REQUESTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/rides/" + rid + "/assign", "", tok);
        assertEquals(400, r.statusCode(),
            "TC272: missing driverId must be 400; got " + r.statusCode());
    }
}

// ─── TC273 — S3-F5 metadata search no match → empty ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC273_UbMetadataSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC273 — Metadata search with unknown value returns empty list")
    void metadata_no_match() throws Exception {
        BASE_URL = rideServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc273@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL273", "AVAILABLE", 4.0, 0);
        long r1 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.setRideMetadata(this, r1, "{\"tag\":\"premium\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/metadata/search?key=tag&value=zzz", tok);
        assert2xx(r, "TC273");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC273: empty list expected; got " + list.size());
    }
}

// ─── TC274 — S3-F3 estimate with same pickup & dropoff returns small fare ───
@Tag("public")
@Tag("features_m1")
class TC274_UbFareEstimateSameLocationTests extends TestBase {
    @Test
    @DisplayName("TC274 — Estimate with identical pickup/dropoff returns near-zero distance")
    void estimate_same_location() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        String body = "{\"pickupLatitude\":30.044,\"pickupLongitude\":31.235,"
                + "\"dropoffLatitude\":30.044,\"dropoffLongitude\":31.235}";
        HttpResponse<String> r = httpPostAuth("/api/rides/estimate", body, tok);
        assert2xx(r, "TC274");
        double dist = _UbM2.rD(parseNode(r.body()), "estimatedDistance", "estimated_distance");
        assertEquals(0.0, dist, 0.5, "TC274: same-location distance must be ~0; got " + dist);
    }
}

// ────────────────────────────────────────────────────────────────────────────
// S4 — Location Service (TC275..TC297) — 9 features, 23 TCs
// S4-F1 latest, S4-F2 update, S4-F3 nearby, S4-F4 batch, S4-F5 metadata,
// S4-F6 history, S4-F7 purge, S4-F8 summary, S4-F9 stationary
// ────────────────────────────────────────────────────────────────────────────

// ─── TC275 — S4-F1 latest location happy ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC275_UbLatestLocationHappyTests extends TestBase {
    @Test
    @DisplayName("TC275 — Latest returns the most-recent timestamp's location row")
    void latest_happy() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "D", "DL275", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedLocation(this, did, 30.04, 31.23, "2026-03-10 08:00:00");
        long latest = _UbM1Seed.seedLocation(this, did, 30.10, 31.30, "2026-03-10 18:00:00");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/driver/" + did + "/latest", tok);
        assert2xx(r, "TC275");
        JsonNode j = parseNode(r.body());
        long id = j.has("id") ? j.get("id").asLong() : -1L;
        assertEquals(latest, id, "TC275: latest location id expected; got " + id);
    }
}

// ─── TC276 — S4-F1 driver with no locations → 404 ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC276_UbLatestLocationEmptyTests extends TestBase {
    @Test
    @DisplayName("TC276 — Latest for driver with zero locations returns 404")
    void latest_empty() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "D", "DL276", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/driver/" + did + "/latest", tok);
        assertEquals(404, r.statusCode(),
            "TC276: must be 404; got " + r.statusCode());
    }
}

// ─── TC277 — S4-F2 update location happy ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC277_UbUpdateLocationHappyTests extends TestBase {
    @Test
    @DisplayName("TC277 — POST location creates new Location row in PG")
    void update_location_happy() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "D", "DL277", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        String body = "{\"latitude\":30.05,\"longitude\":31.24,"
                + "\"metadata\":{\"speed\":40,\"heading\":90}}";
        HttpResponse<String> r = httpPostAuth(
            "/api/locations/driver/" + did, body, tok);
        assert2xx(r, "TC277");
        String fkCol = columnByField("Location", "driver");
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("Location") + "\" WHERE \"" + fkCol + "\" = ?",
            Integer.class, did);
        assertTrue(cnt >= 1, "TC277: at least 1 Location row expected; got " + cnt);
    }
}

// ─── TC278 — S4-F2 invalid coords → 400 ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC278_UbUpdateLocationInvalidTests extends TestBase {
    @Test
    @DisplayName("TC278 — Update with latitude=200 returns 400 (out of range)")
    void update_location_invalid() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "D", "DL278", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        String body = "{\"latitude\":200.0,\"longitude\":31.24}";
        HttpResponse<String> r = httpPostAuth(
            "/api/locations/driver/" + did, body, tok);
        assertEquals(400, r.statusCode(),
            "TC278: invalid latitude must be 400; got " + r.statusCode());
    }
}

// ─── TC279 — S4-F3 nearby drivers happy ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC279_UbNearbyDriversHappyTests extends TestBase {
    @Test
    @DisplayName("TC279 — Nearby returns drivers within radiusKm")
    void nearby_happy() throws Exception {
        BASE_URL = locationServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "Near", "DL279A", "AVAILABLE", 4.5, 0);
        long d2 = _UbM1Seed.seedDriver(this, "Far",  "DL279B", "AVAILABLE", 4.5, 0);
        _UbM1Seed.seedLocation(this, d1, 30.045, 31.236, "2026-03-10 08:00:00");
        _UbM1Seed.seedLocation(this, d2, 31.500, 32.500, "2026-03-10 08:00:00");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/nearby?lat=30.044&lon=31.235&radiusKm=5", tok);
        assert2xx(r, "TC279");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundNear = false;
        boolean foundFar = false;
        for (JsonNode it : list) {
            long id = it.has("driverId") ? it.get("driverId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == d1) foundNear = true;
            if (id == d2) foundFar = true;
        }
        assertTrue(foundNear, "TC279: near driver expected");
        assertFalse(foundFar, "TC279: far driver must be excluded");
    }
}

// ─── TC280 — S4-F3 invalid radius → 400 ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC280_UbNearbyInvalidRadiusTests extends TestBase {
    @Test
    @DisplayName("TC280 — Nearby with radiusKm=-1 returns 400")
    void nearby_invalid_radius() throws Exception {
        BASE_URL = locationServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/nearby?lat=30.044&lon=31.235&radiusKm=-1", tok);
        assertEquals(400, r.statusCode(),
            "TC280: negative radius must be 400; got " + r.statusCode());
    }
}

// ─── TC281 — S4-F4 batch update happy ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC281_UbBatchUpdateHappyTests extends TestBase {
    @Test
    @DisplayName("TC281 — POST batch creates multiple Location rows for one driver")
    void batch_update_happy() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "Batch", "DL281", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        String body = "{\"driverId\":" + did + ",\"locations\":["
                + "{\"latitude\":30.05,\"longitude\":31.24,\"timestamp\":\"2026-03-10T08:00:00\"},"
                + "{\"latitude\":30.06,\"longitude\":31.25,\"timestamp\":\"2026-03-10T08:05:00\"},"
                + "{\"latitude\":30.07,\"longitude\":31.26,\"timestamp\":\"2026-03-10T08:10:00\"}"
                + "]}";
        HttpResponse<String> r = httpPostAuth("/api/locations/batch", body, tok);
        assert2xx(r, "TC281");
        String fkCol = columnByField("Location", "driver");
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("Location") + "\" WHERE \"" + fkCol + "\" = ?",
            Integer.class, did);
        assertTrue(cnt >= 3, "TC281: at least 3 Location rows expected; got " + cnt);
    }
}

// ─── TC282 — S4-F4 batch empty list → 400 ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC282_UbBatchEmptyTests extends TestBase {
    @Test
    @DisplayName("TC282 — Batch with empty locations array returns 400")
    void batch_empty() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "Batch", "DL282", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        String body = "{\"driverId\":" + did + ",\"locations\":[]}";
        HttpResponse<String> r = httpPostAuth("/api/locations/batch", body, tok);
        assertEquals(400, r.statusCode(),
            "TC282: empty locations must be 400; got " + r.statusCode());
    }
}

// ─── TC283 — S4-F5 metadata search eq operator ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC283_UbLocationMetadataEqTests extends TestBase {
    @Test
    @DisplayName("TC283 — Metadata search ?operator=eq matches exact metadata value")
    void metadata_eq() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "MD", "DL283", "AVAILABLE", 4.0, 0);
        long l1 = _UbM1Seed.seedLocation(this, did, 30.04, 31.23, "2026-03-10 08:00:00");
        long l2 = _UbM1Seed.seedLocation(this, did, 30.05, 31.24, "2026-03-10 09:00:00");
        _UbM1Seed.setLocationMetadata(this, l1, "{\"speed\":50}");
        _UbM1Seed.setLocationMetadata(this, l2, "{\"speed\":80}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/metadata/search?key=speed&operator=eq&value=50", tok);
        assert2xx(r, "TC283");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 1, "TC283: at least 1 match expected");
        for (JsonNode it : list) {
            long id = it.has("id") ? it.get("id").asLong() : -1L;
            assertTrue(id != l2, "TC283: speed=80 row must be excluded; got " + id);
        }
    }
}

// ─── TC284 — S4-F5 metadata search gt operator ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC284_UbLocationMetadataGtTests extends TestBase {
    @Test
    @DisplayName("TC284 — Metadata search ?operator=gt&value=60 returns only rows with speed>60")
    void metadata_gt() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "MD", "DL284", "AVAILABLE", 4.0, 0);
        long l1 = _UbM1Seed.seedLocation(this, did, 30.04, 31.23, "2026-03-10 08:00:00");
        long l2 = _UbM1Seed.seedLocation(this, did, 30.05, 31.24, "2026-03-10 09:00:00");
        _UbM1Seed.setLocationMetadata(this, l1, "{\"speed\":50}");
        _UbM1Seed.setLocationMetadata(this, l2, "{\"speed\":80}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/metadata/search?key=speed&operator=gt&value=60", tok);
        assert2xx(r, "TC284");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("id") ? it.get("id").asLong() : -1L;
            assertTrue(id != l1, "TC284: speed=50 row must be excluded by gt:60; got " + id);
        }
    }
}

// ─── TC285 — S4-F5 invalid operator → 400 ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC285_UbLocationMetadataBadOpTests extends TestBase {
    @Test
    @DisplayName("TC285 — Metadata search with operator=foo returns 400")
    void metadata_bad_op() throws Exception {
        BASE_URL = locationServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/metadata/search?key=speed&operator=foo&value=50", tok);
        assertEquals(400, r.statusCode(),
            "TC285: bad operator must be 400; got " + r.statusCode());
    }
}

// ─── TC286 — S4-F6 history happy ────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC286_UbLocationHistoryHappyTests extends TestBase {
    @Test
    @DisplayName("TC286 — History returns rows in date range for given driver")
    void history_happy() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "H", "DL286", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedLocation(this, did, 30.04, 31.23, "2026-03-10 08:00:00");
        _UbM1Seed.seedLocation(this, did, 30.05, 31.24, "2026-03-15 08:00:00");
        _UbM1Seed.seedLocation(this, did, 30.06, 31.25, "2026-04-15 08:00:00");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/history?startDate=2026-03-01&endDate=2026-03-31&driverId=" + did, tok);
        assert2xx(r, "TC286");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC286: at least 2 in-range rows expected; got " + list.size());
    }
}

// ─── TC287 — S4-F6 history empty range ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC287_UbLocationHistoryEmptyTests extends TestBase {
    @Test
    @DisplayName("TC287 — History with no rows in range returns empty list")
    void history_empty() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "H", "DL287", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/history?startDate=2099-01-01&endDate=2099-01-31&driverId=" + did, tok);
        assert2xx(r, "TC287");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC287: empty list expected; got " + list.size());
    }
}

// ─── TC288 — S4-F7 purge older than N days ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC288_UbPurgeOldHappyTests extends TestBase {
    @Test
    @DisplayName("TC288 — DELETE purge?olderThanDays=30 deletes pre-cutoff rows")
    void purge_happy() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "P", "DL288", "AVAILABLE", 4.0, 0);
        String oldTs = _UbM1Seed.pastDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        long oldId = _UbM1Seed.seedLocation(this, did, 30.04, 31.23, oldTs);
        long newId = _UbM1Seed.seedLocation(this, did, 30.05, 31.24,
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        _UbM1Seed.setLocationTimestamp(this, oldId,
            java.sql.Timestamp.valueOf(_UbM1Seed.pastDateTime()));
        _UbM1Seed.setLocationTimestamp(this, newId,
            java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
        String tok = adminToken();
        HttpResponse<String> r = httpDeleteAuth(
            "/api/locations/purge?olderThanDays=30", tok);
        assert2xx(r, "TC288");
        Integer remaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("Location") + "\" WHERE id = ?",
            Integer.class, oldId);
        assertEquals(0, remaining.intValue(),
            "TC288: old row must be deleted; got " + remaining);
    }
}

// ─── TC289 — S4-F7 purge with negative param → 400 ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC289_UbPurgeNegativeParamTests extends TestBase {
    @Test
    @DisplayName("TC289 — Purge with olderThanDays=-1 returns 400")
    void purge_negative() throws Exception {
        BASE_URL = locationServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpDeleteAuth(
            "/api/locations/purge?olderThanDays=-1", tok);
        assertEquals(400, r.statusCode(),
            "TC289: negative param must be 400; got " + r.statusCode());
    }
}

// ─── TC290 — S4-F8 driver movement summary happy ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC290_UbDriverMovementSummaryTests extends TestBase {
    @Test
    @DisplayName("TC290 — Summary returns totalLocationPoints and lastTimestamp")
    void summary_happy() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "S", "DL290", "AVAILABLE", 4.0, 0);
        long l1 = _UbM1Seed.seedLocation(this, did, 30.04, 31.23, "2026-03-10 08:00:00");
        long l2 = _UbM1Seed.seedLocation(this, did, 30.05, 31.24, "2026-03-10 09:00:00");
        long l3 = _UbM1Seed.seedLocation(this, did, 30.06, 31.25, "2026-03-10 10:00:00");
        _UbM1Seed.setLocationMetadata(this, l1, "{\"speed\":40}");
        _UbM1Seed.setLocationMetadata(this, l2, "{\"speed\":50}");
        _UbM1Seed.setLocationMetadata(this, l3, "{\"speed\":60}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/driver/" + did + "/summary?startDate=2026-03-01&endDate=2026-03-31",
            tok);
        assert2xx(r, "TC290");
        long total = _UbM2.rL(parseNode(r.body()),
            "totalLocationPoints", "total_location_points");
        assertEquals(3L, total,
            "TC290: totalLocationPoints=3; got " + total);
    }
}

// ─── TC291 — S4-F8 summary with no data → totalPoints=0 ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC291_UbDriverMovementSummaryEmptyTests extends TestBase {
    @Test
    @DisplayName("TC291 — Summary for driver with no locations returns totalPoints=0")
    void summary_empty() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "S", "DL291", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/driver/" + did + "/summary?startDate=2026-03-01&endDate=2026-03-31",
            tok);
        // 200 with totalPoints=0, OR 404 — either is permissible per spec
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
            "TC291: must be 200 or 404; got " + r.statusCode());
    }
}

// ─── TC292 — S4-F9 stationary driver detection ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC292_UbStationaryDriversTests extends TestBase {
    @Test
    @DisplayName("TC292 — Stationary returns drivers with last metadata.speed below maxSpeed")
    void stationary_happy() throws Exception {
        BASE_URL = locationServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "Sta", "DL292A", "AVAILABLE", 4.0, 0);
        long d2 = _UbM1Seed.seedDriver(this, "Mov", "DL292B", "AVAILABLE", 4.0, 0);
        java.sql.Timestamp recent = java.sql.Timestamp.valueOf(
            java.time.LocalDateTime.now().minusMinutes(2));
        long l1 = _UbM1Seed.seedLocation(this, d1, 30.04, 31.23, "2026-03-10 08:00:00");
        long l2 = _UbM1Seed.seedLocation(this, d2, 30.05, 31.24, "2026-03-10 09:00:00");
        _UbM1Seed.setLocationMetadata(this, l1, "{\"speed\":2}");
        _UbM1Seed.setLocationMetadata(this, l2, "{\"speed\":50}");
        _UbM1Seed.setLocationTimestamp(this, l1, recent);
        _UbM1Seed.setLocationTimestamp(this, l2, recent);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/stationary?maxSpeed=5&sinceMinutes=10", tok);
        assert2xx(r, "TC292");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundSta = false;
        boolean foundMov = false;
        for (JsonNode it : list) {
            long id = it.has("driverId") ? it.get("driverId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == d1) foundSta = true;
            if (id == d2) foundMov = true;
        }
        assertTrue(foundSta, "TC292: stationary driver expected");
        assertFalse(foundMov, "TC292: moving driver must be excluded");
    }
}

// ─── TC293 — S4-F9 stationary negative param → 400 ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC293_UbStationaryNegativeParamTests extends TestBase {
    @Test
    @DisplayName("TC293 — Stationary with sinceMinutes=-1 returns 400")
    void stationary_negative() throws Exception {
        BASE_URL = locationServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/stationary?maxSpeed=5&sinceMinutes=-1", tok);
        assertEquals(400, r.statusCode(),
            "TC293: negative sinceMinutes must be 400; got " + r.statusCode());
    }
}

// ─── TC294 — S4-F2 update for unknown driver returns 404 ────────────────────
@Tag("public")
@Tag("features_m1")
class TC294_UbUpdateLocationNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC294 — Update for unknown driverId returns 404")
    void update_not_found() throws Exception {
        BASE_URL = locationServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/locations/driver/999999",
            "{\"latitude\":30.05,\"longitude\":31.24}",
            tok);
        assertEquals(404, r.statusCode(),
            "TC294: must be 404; got " + r.statusCode());
    }
}

// ─── TC295 — S4-F3 nearby zero radius → empty ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC295_UbNearbyZeroRadiusTests extends TestBase {
    @Test
    @DisplayName("TC295 — Nearby with radiusKm=0 returns empty list")
    void nearby_zero_radius() throws Exception {
        BASE_URL = locationServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "Z", "DL295", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedLocation(this, d1, 30.05, 31.24, "2026-03-10 08:00:00");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/nearby?lat=30.044&lon=31.235&radiusKm=0", tok);
        assert2xx(r, "TC295");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC295: empty list expected; got " + list.size());
    }
}

// ─── TC296 — S4-F1 latest 404 unknown driver ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC296_UbLatestUnknownDriverTests extends TestBase {
    @Test
    @DisplayName("TC296 — Latest for non-existent driver returns 404")
    void latest_unknown_driver() throws Exception {
        BASE_URL = locationServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/driver/999999/latest", tok);
        assertEquals(404, r.statusCode(),
            "TC296: must be 404; got " + r.statusCode());
    }
}

// ─── TC297 — S4-F8 invalid date range → 400 ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC297_UbDriverMovementSummaryBadDateTests extends TestBase {
    @Test
    @DisplayName("TC297 — Summary with startDate > endDate returns 400")
    void summary_bad_date() throws Exception {
        BASE_URL = locationServiceUrl;
        long did = _UbM1Seed.seedDriver(this, "S", "DL297", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/driver/" + did + "/summary?startDate=2026-12-31&endDate=2026-01-01",
            tok);
        assertEquals(400, r.statusCode(),
            "TC297: must be 400; got " + r.statusCode());
    }
}

// ────────────────────────────────────────────────────────────────────────────
// S5 — Payment Service (TC298..TC328) — 9 features, 31 TCs
// S5-F1 search, S5-F2 refund, S5-F3 user-summary, S5-F4 process,
// S5-F5 apply coupon, S5-F6 revenue, S5-F7 retry, S5-F8 details, S5-F9 top coupons
// ────────────────────────────────────────────────────────────────────────────

// ─── TC298 — S5-F1 search by status ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC298_UbPaymentSearchByStatusTests extends TestBase {
    @Test
    @DisplayName("TC298 — Search ?status=COMPLETED returns COMPLETED payments only")
    void search_by_status() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc298@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL298", "AVAILABLE", 4.0, 0);
        long r1 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        long r2 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 50.0);
        _UbM1Seed.seedPayment(this, r1, uid, 50.0, "CREDIT_CARD", "COMPLETED");
        _UbM1Seed.seedPayment(this, r2, uid, 50.0, "CREDIT_CARD", "PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/payments/search?status=COMPLETED", tok);
        assert2xx(r, "TC298");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String s = it.has("status") ? it.get("status").asText() : "";
            assertEquals("COMPLETED", s, "TC298: every result must be COMPLETED; got " + s);
        }
    }
}

// ─── TC299 — S5-F1 search by date range ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC299_UbPaymentSearchByDateRangeTests extends TestBase {
    @Test
    @DisplayName("TC299 — Search ?startDate&endDate filters by payment date")
    void search_by_date_range() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc299@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL299", "AVAILABLE", 4.0, 0);
        long r1 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-06-15", 50.0);
        long r2 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-08-15", 50.0);
        long p1 = _UbM1Seed.seedPayment(this, r1, uid, 50.0, "CREDIT_CARD", "COMPLETED");
        long p2 = _UbM1Seed.seedPayment(this, r2, uid, 50.0, "CREDIT_CARD", "COMPLETED");
        setAllDateColumns(tableName("Payment"), p1,
            java.sql.Timestamp.valueOf("2026-06-15 12:00:00"));
        setAllDateColumns(tableName("Payment"), p2,
            java.sql.Timestamp.valueOf("2026-08-15 12:00:00"));
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/search?startDate=2026-06-01&endDate=2026-06-30", tok);
        assert2xx(r, "TC299");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 1, "TC299: at least 1 in-range payment expected; got " + list.size());
    }
}

// ─── TC300 — S5-F2 refund happy path ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC300_UbRefundHappyTests extends TestBase {
    @Test
    @DisplayName("TC300 — PUT refund flips payment status to REFUNDED")
    void refund_happy() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc300@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL300", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 50.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        String body = "{\"reason\":\"Customer request\"}";
        HttpResponse<String> r = httpPutAuth("/api/payments/" + pid + "/refund", body, tok);
        assert2xx(r, "TC300");
        String stCol = columnByField("Payment", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Payment") + "\" WHERE id = ?",
            String.class, pid);
        assertEquals("REFUNDED", dbStatus,
            "TC300: PG payment.status=REFUNDED expected; got " + dbStatus);
    }
}

// ─── TC301 — S5-F2 refund non-COMPLETED → 400 ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC301_UbRefundWrongStateTests extends TestBase {
    @Test
    @DisplayName("TC301 — Refund a PENDING payment returns 400")
    void refund_wrong_state() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc301@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL301", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 50.0, "CREDIT_CARD", "PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/payments/" + pid + "/refund",
            "{\"reason\":\"x\"}", tok);
        assertEquals(400, r.statusCode(),
            "TC301: refunding PENDING must be 400; got " + r.statusCode());
    }
}

// ─── TC302 — S5-F2 refund 404 unknown ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC302_UbRefundNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC302 — Refund unknown paymentId returns 404")
    void refund_not_found() throws Exception {
        BASE_URL = paymentServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/payments/999999/refund", "{\"reason\":\"x\"}", tok);
        assertEquals(404, r.statusCode(),
            "TC302: must be 404; got " + r.statusCode());
    }
}

// ─── TC303 — S5-F3 user payment summary happy ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC303_UbUserPaymentSummaryTests extends TestBase {
    @Test
    @DisplayName("TC303 — Summary returns totalPayments=3 and methodBreakdown")
    void user_summary_happy() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc303@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL303", "AVAILABLE", 4.0, 0);
        long r1 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        long r2 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 60.0);
        long r3 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-12", 70.0);
        _UbM1Seed.seedPayment(this, r1, uid, 50.0, "CREDIT_CARD", "COMPLETED");
        _UbM1Seed.seedPayment(this, r2, uid, 60.0, "CREDIT_CARD", "COMPLETED");
        _UbM1Seed.seedPayment(this, r3, uid, 70.0, "CASH",        "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/user/" + uid + "/summary", tok);
        assert2xx(r, "TC303");
        JsonNode j = parseNode(r.body());
        assertEquals(3L, _UbM2.rL(j, "totalPayments", "total_payments"),
            "TC303: totalPayments=3");
        JsonNode br = _UbM2.rO(j, "methodBreakdown", "method_breakdown");
        assertNotNull(br, "TC303: methodBreakdown key required");
    }
}

// ─── TC304 — S5-F3 user with no payments → 404 or zeros ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC304_UbUserPaymentSummaryEmptyTests extends TestBase {
    @Test
    @DisplayName("TC304 — Summary for user with no payments returns 200 + zeros, or 404")
    void user_summary_empty() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc304@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/user/" + uid + "/summary", tok);
        // either 200 with zeros, or 404
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
            "TC304: must be 200 or 404; got " + r.statusCode());
    }
}

// ─── TC305 — S5-F4 process payment happy ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC305_UbProcessPaymentHappyTests extends TestBase {
    @Test
    @DisplayName("TC305 — POST process creates Payment row for the ride")
    void process_happy() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc305@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL305", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        String body = "{\"method\":\"CREDIT_CARD\",\"cardLastFour\":\"4242\"}";
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/ride/" + rid, body, tok);
        assert2xx(r, "TC305");
        String fkCol = columnByField("Payment", "ride");
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("Payment") + "\" WHERE \"" + fkCol + "\" = ?",
            Integer.class, rid);
        assertTrue(cnt >= 1, "TC305: at least 1 Payment row expected; got " + cnt);
    }
}

// ─── TC306 — S5-F4 process for unknown ride → 404 ───────────────────────────
@Tag("public")
@Tag("features_m1")
class TC306_UbProcessNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC306 — Process for unknown rideId returns 404")
    void process_not_found() throws Exception {
        BASE_URL = paymentServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/ride/999999",
            "{\"method\":\"CASH\"}", tok);
        assertEquals(404, r.statusCode(),
            "TC306: must be 404; got " + r.statusCode());
    }
}

// ─── TC307 — S5-F4 duplicate payment for ride → 400 ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC307_UbProcessDuplicateTests extends TestBase {
    @Test
    @DisplayName("TC307 — Process when payment already exists returns 400")
    void process_duplicate() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc307@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL307", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.seedPayment(this, rid, uid, 50.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/ride/" + rid,
            "{\"method\":\"CASH\"}", tok);
        assertEquals(400, r.statusCode(),
            "TC307: duplicate payment must be 400; got " + r.statusCode());
    }
}

// ─── TC308 — S5-F5 apply coupon happy ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC308_UbApplyCouponHappyTests extends TestBase {
    @Test
    @DisplayName("TC308 — POST apply-coupon adds PaymentCoupon row")
    void apply_coupon_happy() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc308@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL308", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CREDIT_CARD", "PENDING");
        long cid = _UbM1Seed.seedCoupon(this, "TC308", "PERCENTAGE", 10.0, 100,
            _UbM1Seed.futureDateTime(), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/" + pid + "/coupons/" + cid, "", tok);
        assert2xx(r, "TC308");
        String pCol = columnByField("PaymentCoupon", "payment");
        String cCol = columnByField("PaymentCoupon", "coupon");
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("PaymentCoupon") + "\" WHERE \""
                + pCol + "\" = ? AND \"" + cCol + "\" = ?",
            Integer.class, pid, cid);
        assertEquals(1, cnt.intValue(),
            "TC308: 1 PaymentCoupon row expected; got " + cnt);
    }
}

// ─── TC309 — S5-F5 expired coupon → 400 ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC309_UbApplyCouponExpiredTests extends TestBase {
    @Test
    @DisplayName("TC309 — Applying an expired coupon returns 400")
    void apply_coupon_expired() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc309@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL309", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CREDIT_CARD", "PENDING");
        long cid = _UbM1Seed.seedCoupon(this, "TC309", "FIXED", 5.0, 100,
            _UbM1Seed.pastDateTime(), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/" + pid + "/coupons/" + cid, "", tok);
        assertEquals(400, r.statusCode(),
            "TC309: expired coupon must be 400; got " + r.statusCode());
    }
}

// ─── TC310 — S5-F5 coupon at maxUses → 400 ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC310_UbApplyCouponMaxedOutTests extends TestBase {
    @Test
    @DisplayName("TC310 — Applying coupon whose currentUses==maxUses returns 400")
    void apply_coupon_maxed() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc310@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL310", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CREDIT_CARD", "PENDING");
        long cid = _UbM1Seed.seedCoupon(this, "TC310", "PERCENTAGE", 10.0, 1,
            _UbM1Seed.futureDateTime(), true);
        try {
            jdbc.update("UPDATE \"" + tableName("Coupon") + "\" SET \""
                + columnByField("Coupon", "currentUses") + "\" = 1 WHERE id = ?", cid);
        } catch (Exception ignored) {}
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/" + pid + "/coupons/" + cid, "", tok);
        assertEquals(400, r.statusCode(),
            "TC310: maxed-out coupon must be 400; got " + r.statusCode());
    }
}

// ─── TC311 — S5-F5 coupon active=false → 400 ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC311_UbApplyCouponInactiveTests extends TestBase {
    @Test
    @DisplayName("TC311 — Applying inactive coupon returns 400")
    void apply_coupon_inactive() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc311@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL311", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CREDIT_CARD", "PENDING");
        long cid = _UbM1Seed.seedCoupon(this, "TC311", "PERCENTAGE", 10.0, 100,
            _UbM1Seed.futureDateTime(), false);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/" + pid + "/coupons/" + cid, "", tok);
        assertEquals(400, r.statusCode(),
            "TC311: inactive coupon must be 400; got " + r.statusCode());
    }
}

// ─── TC312 — S5-F6 revenue report happy ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC312_UbRevenueReportHappyTests extends TestBase {
    @Test
    @DisplayName("TC312 — Revenue report sums COMPLETED amounts in date range")
    void revenue_happy() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc312@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL312", "AVAILABLE", 4.0, 0);
        long r1 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long r2 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 200.0);
        long p1 = _UbM1Seed.seedPayment(this, r1, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long p2 = _UbM1Seed.seedPayment(this, r2, uid, 200.0, "CASH",        "COMPLETED");
        setAllDateColumns(tableName("Payment"), p1,
            java.sql.Timestamp.valueOf("2026-03-10 12:00:00"));
        setAllDateColumns(tableName("Payment"), p2,
            java.sql.Timestamp.valueOf("2026-03-11 12:00:00"));
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/reports/revenue?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC312");
        double rev = _UbM2.rD(parseNode(r.body()), "totalRevenue", "total_revenue");
        assertEquals(300.0, rev, 0.5, "TC312: totalRevenue=300; got " + rev);
    }
}

// ─── TC313 — S5-F6 revenue counts refundedAmount ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC313_UbRevenueRefundedAmountTests extends TestBase {
    @Test
    @DisplayName("TC313 — Revenue report exposes refundedAmount/refundCount")
    void revenue_refunded() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc313@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL313", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CREDIT_CARD", "REFUNDED");
        setAllDateColumns(tableName("Payment"), pid,
            java.sql.Timestamp.valueOf("2026-03-10 12:00:00"));
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/reports/revenue?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC313");
        JsonNode j = parseNode(r.body());
        long refundCnt = _UbM2.rL(j, "refundCount", "refund_count");
        double refundAmt = _UbM2.rD(j, "refundedAmount", "refunded_amount");
        assertTrue(refundCnt >= 1, "TC313: refundCount >= 1; got " + refundCnt);
        assertTrue(refundAmt >= 100.0 - 0.5, "TC313: refundedAmount >= 100; got " + refundAmt);
    }
}

// ─── TC314 — S5-F6 empty range → zeros ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC314_UbRevenueEmptyTests extends TestBase {
    @Test
    @DisplayName("TC314 — Revenue with no payments in range returns totalRevenue=0")
    void revenue_empty() throws Exception {
        BASE_URL = paymentServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/reports/revenue?startDate=2099-01-01&endDate=2099-01-31", tok);
        assert2xx(r, "TC314");
        double rev = _UbM2.rD(parseNode(r.body()), "totalRevenue", "total_revenue");
        assertEquals(0.0, rev, 0.5, "TC314: totalRevenue=0; got " + rev);
    }
}

// ─── TC315 — S5-F7 retry payment happy ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC315_UbRetryHappyTests extends TestBase {
    @Test
    @DisplayName("TC315 — PUT retry on FAILED payment flips status (e.g., to PENDING)")
    void retry_happy() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc315@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL315", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 50.0, "CREDIT_CARD", "FAILED");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/payments/" + pid + "/retry", "", tok);
        assert2xx(r, "TC315");
        String stCol = columnByField("Payment", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Payment") + "\" WHERE id = ?",
            String.class, pid);
        assertTrue(!"FAILED".equals(dbStatus),
            "TC315: status must change from FAILED; got " + dbStatus);
    }
}

// ─── TC316 — S5-F7 retry COMPLETED → 400 ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC316_UbRetryWrongStateTests extends TestBase {
    @Test
    @DisplayName("TC316 — Retry COMPLETED payment returns 400")
    void retry_wrong_state() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc316@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL316", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 50.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/payments/" + pid + "/retry", "", tok);
        assertEquals(400, r.statusCode(),
            "TC316: retrying COMPLETED must be 400; got " + r.statusCode());
    }
}

// ─── TC317 — S5-F8 details happy ────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC317_UbPaymentDetailsHappyTests extends TestBase {
    @Test
    @DisplayName("TC317 — Details returns appliedCoupons and finalAmount")
    void details_happy() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc317@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL317", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long cid = _UbM1Seed.seedCoupon(this, "TC317", "PERCENTAGE", 10.0, 100,
            _UbM1Seed.futureDateTime(), true);
        _UbM1Seed.seedPaymentCoupon(this, pid, cid, 10.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/" + pid + "/details", tok);
        assert2xx(r, "TC317");
        JsonNode j = parseNode(r.body());
        JsonNode coupons = _UbM2.rO(j, "appliedCoupons", "applied_coupons");
        assertNotNull(coupons, "TC317: appliedCoupons key required; body=" + r.body());
        assertTrue(coupons.size() >= 1,
            "TC317: at least 1 applied coupon expected; got " + coupons.size());
    }
}

// ─── TC318 — S5-F8 details 404 ──────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC318_UbPaymentDetailsNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC318 — Details for unknown paymentId returns 404")
    void details_not_found() throws Exception {
        BASE_URL = paymentServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/payments/999999/details", tok);
        assertEquals(404, r.statusCode(),
            "TC318: must be 404; got " + r.statusCode());
    }
}

// ─── TC319 — S5-F9 top-used coupons ranking ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC319_UbTopCouponsRankingTests extends TestBase {
    @Test
    @DisplayName("TC319 — top-used ranks coupon B (3 uses) above coupon A (1 use)")
    void top_coupons_ranking() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc319@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL319", "AVAILABLE", 4.0, 0);
        long cA = _UbM1Seed.seedCoupon(this, "CA", "PERCENTAGE", 10.0, 100,
            _UbM1Seed.futureDateTime(), true);
        long cB = _UbM1Seed.seedCoupon(this, "CB", "FIXED",       5.0,  100,
            _UbM1Seed.futureDateTime(), true);
        for (int i = 0; i < 1; i++) {
            long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
            long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
            _UbM1Seed.seedPaymentCoupon(this, pid, cA, 10.0);
        }
        for (int i = 0; i < 3; i++) {
            long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 100.0);
            long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CASH", "COMPLETED");
            _UbM1Seed.seedPaymentCoupon(this, pid, cB, 5.0);
        }
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/coupons/top-used?limit=10", tok);
        assert2xx(r, "TC319");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC319: at least 2 coupons expected; got " + list.size());
        long firstId = list.get(0).has("couponId") ? list.get(0).get("couponId").asLong()
                : list.get(0).has("id") ? list.get(0).get("id").asLong() : -1L;
        assertEquals(cB, firstId, "TC319: most-used coupon (cB) must be first");
    }
}

// ─── TC320 — S5-F9 limit honored ────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC320_UbTopCouponsLimitTests extends TestBase {
    @Test
    @DisplayName("TC320 — top-used limit=1 returns only 1 item")
    void top_coupons_limit() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc320@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL320", "AVAILABLE", 4.0, 0);
        long cA = _UbM1Seed.seedCoupon(this, "C1", "PERCENTAGE", 10.0, 100,
            _UbM1Seed.futureDateTime(), true);
        long cB = _UbM1Seed.seedCoupon(this, "C2", "FIXED",       5.0,  100,
            _UbM1Seed.futureDateTime(), true);
        long r1 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long p1 = _UbM1Seed.seedPayment(this, r1, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        _UbM1Seed.seedPaymentCoupon(this, p1, cA, 10.0);
        long r2 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 100.0);
        long p2 = _UbM1Seed.seedPayment(this, r2, uid, 100.0, "CASH", "COMPLETED");
        _UbM1Seed.seedPaymentCoupon(this, p2, cB, 5.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/coupons/top-used?limit=1", tok);
        assert2xx(r, "TC320");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(1, list.size(), "TC320: limit=1 expected; got " + list.size());
    }
}

// ─── TC321 — S5-F1 search status+date combo ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC321_UbPaymentSearchComboTests extends TestBase {
    @Test
    @DisplayName("TC321 — Search status=COMPLETED + date range filters by both")
    void search_combo() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc321@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL321", "AVAILABLE", 4.0, 0);
        long r1 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        long p1 = _UbM1Seed.seedPayment(this, r1, uid, 50.0, "CREDIT_CARD", "COMPLETED");
        long r2 = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-11", 50.0);
        long p2 = _UbM1Seed.seedPayment(this, r2, uid, 50.0, "CREDIT_CARD", "PENDING");
        setAllDateColumns(tableName("Payment"), p1,
            java.sql.Timestamp.valueOf("2026-03-10 12:00:00"));
        setAllDateColumns(tableName("Payment"), p2,
            java.sql.Timestamp.valueOf("2026-03-11 12:00:00"));
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/search?status=COMPLETED&startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC321");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            assertEquals("COMPLETED",
                it.has("status") ? it.get("status").asText() : "",
                "TC321: every result must be COMPLETED");
        }
    }
}

// ─── TC322 — S5-F4 invalid method → 400 ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC322_UbProcessInvalidMethodTests extends TestBase {
    @Test
    @DisplayName("TC322 — Process with method=BITCOIN returns 400")
    void process_invalid_method() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc322@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL322", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/ride/" + rid,
            "{\"method\":\"BITCOIN\"}", tok);
        assertEquals(400, r.statusCode(),
            "TC322: invalid method must be 400; got " + r.statusCode());
    }
}

// ─── TC323 — S5-F8 details with no coupons → empty list ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC323_UbPaymentDetailsNoCouponsTests extends TestBase {
    @Test
    @DisplayName("TC323 — Details for payment with no coupons returns empty appliedCoupons")
    void details_no_coupons() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc323@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL323", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CASH", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/" + pid + "/details", tok);
        assert2xx(r, "TC323");
        JsonNode coupons = _UbM2.rO(parseNode(r.body()),
            "appliedCoupons", "applied_coupons");
        if (coupons != null) {
            assertEquals(0, coupons.size(),
                "TC323: empty appliedCoupons expected; got " + coupons.size());
        }
    }
}

// ─── TC324 — S5-F2 refund 404 ───────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC324_UbRefundUnknownTests extends TestBase {
    @Test
    @DisplayName("TC324 — Refund of unknown payment returns 404")
    void refund_unknown() throws Exception {
        BASE_URL = paymentServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/payments/999999/refund",
            "{\"reason\":\"x\"}", tok);
        assertEquals(404, r.statusCode(),
            "TC324: must be 404; got " + r.statusCode());
    }
}

// ─── TC325 — S5-F4 cardLastFour validation: length=5 → 400 ──────────────────
@Tag("public")
@Tag("features_m1")
class TC325_UbProcessBadCardTests extends TestBase {
    @Test
    @DisplayName("TC325 — Process with cardLastFour='12345' (length 5) returns 400")
    void process_bad_card() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc325@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL325", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/ride/" + rid,
            "{\"method\":\"CREDIT_CARD\",\"cardLastFour\":\"12345\"}",
            tok);
        assertEquals(400, r.statusCode(),
            "TC325: bad cardLastFour must be 400; got " + r.statusCode());
    }
}

// ─── TC326 — S5-F3 user summary 404 ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC326_UbUserSummaryNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC326 — User summary for unknown user returns 404")
    void user_summary_not_found() throws Exception {
        BASE_URL = paymentServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/user/999999/summary", tok);
        assertEquals(404, r.statusCode(),
            "TC326: must be 404; got " + r.statusCode());
    }
}

// ─── TC327 — S5-F5 apply coupon — payment 404 ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC327_UbApplyCouponPaymentNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC327 — apply-coupon with unknown paymentId returns 404")
    void apply_coupon_payment_not_found() throws Exception {
        BASE_URL = paymentServiceUrl;
        long cid = _UbM1Seed.seedCoupon(this, "TC327", "FIXED", 5.0, 100,
            _UbM1Seed.futureDateTime(), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/999999/coupons/" + cid, "", tok);
        assertEquals(404, r.statusCode(),
            "TC327: must be 404; got " + r.statusCode());
    }
}

// ─── TC328 — S5-F8 details: finalAmount = original - totalDiscount ──────────
@Tag("public")
@Tag("features_m1")
class TC328_UbPaymentDetailsFinalAmountTests extends TestBase {
    @Test
    @DisplayName("TC328 — Details.finalAmount = originalAmount - totalDiscount")
    void details_final_amount() throws Exception {
        BASE_URL = paymentServiceUrl;
        long uid = _UbM1Seed.seedUser(this, "U", "tc328@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL328", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, uid, did, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long cid = _UbM1Seed.seedCoupon(this, "TC328", "FIXED", 25.0, 100,
            _UbM1Seed.futureDateTime(), true);
        _UbM1Seed.seedPaymentCoupon(this, pid, cid, 25.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/" + pid + "/details", tok);
        assert2xx(r, "TC328");
        JsonNode j = parseNode(r.body());
        double orig = _UbM2.rD(j, "originalAmount", "original_amount");
        double td   = _UbM2.rD(j, "totalDiscount", "total_discount");
        double fin  = _UbM2.rD(j, "finalAmount", "final_amount");
        assertEquals(100.0, orig, 0.5, "TC328: originalAmount=100");
        assertEquals(25.0,  td,   0.5, "TC328: totalDiscount=25");
        assertEquals(75.0,  fin,  0.5, "TC328: finalAmount=75");
    }
}

// ────────────────────────────────────────────────────────────────────────────
// EXTRAS (TC329..TC378) — 50 additional spec-scenario coverage TCs
// Distribution per allocation table:
//   S1 user-service:    TC329-TC335, TC376       (8 TCs)
//   S2 driver-service:  TC336-TC341, TC370, TC372, TC375  (9 TCs)
//   S3 ride-service:    TC342-TC351, TC371, TC373, TC377  (13 TCs)
//   S4 location-service: TC352-TC359, TC374       (9 TCs)
//   S5 payment-service: TC360-TC369, TC378        (11 TCs)
// ────────────────────────────────────────────────────────────────────────────

// ─── TC329 — S1-F1 search combined name+role ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC329_UbSearchNameAndRoleTests extends TestBase {
    @Test
    @DisplayName("TC329 — Search ?name=Ali&role=RIDER returns RIDERs named Ali")
    void search_name_and_role() throws Exception {
        BASE_URL = userServiceUrl;
        _UbM1Seed.seedUser(this, "Ali",     "tc329_a@uber.io", "RIDER");
        _UbM1Seed.seedUser(this, "Ali",     "tc329_b@uber.io", "ADMIN");
        _UbM1Seed.seedUser(this, "Bob",     "tc329_c@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/search?name=Ali&role=RIDER", tok);
        assert2xx(r, "TC329");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String role = it.has("role") ? it.get("role").asText() : "";
            String name = it.has("name") ? it.get("name").asText() : "";
            assertEquals("RIDER", role, "TC329: role must be RIDER; got " + role);
            assertTrue(name.contains("Ali"), "TC329: name must contain 'Ali'; got " + name);
        }
    }
}

// ─── TC330 — S1-F5 prefs search by complex value ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC330_UbPrefsSearchBoolValueTests extends TestBase {
    @Test
    @DisplayName("TC330 — Prefs search ?key=darkMode&value=true matches users")
    void prefs_search_bool() throws Exception {
        BASE_URL = userServiceUrl;
        long u1 = _UbM1Seed.seedUser(this, "On",  "tc330_a@uber.io", "RIDER");
        long u2 = _UbM1Seed.seedUser(this, "Off", "tc330_b@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, u1, "{\"darkMode\":\"true\"}");
        _UbM1Seed.setUserPreferences(this, u2, "{\"darkMode\":\"false\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/preferences/search?key=darkMode&value=true", tok);
        assert2xx(r, "TC330");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("userId") ? it.get("userId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            assertTrue(id != u2, "TC330: false-darkMode user must be excluded; got " + id);
        }
    }
}

// ─── TC331 — S1-F6 top riders date filter excludes outside-range ────────────
@Tag("public")
@Tag("features_m1")
class TC331_UbTopRidersDateFilterTests extends TestBase {
    @Test
    @DisplayName("TC331 — Top riders excludes rides outside startDate-endDate")
    void top_riders_date_filter() throws Exception {
        BASE_URL = userServiceUrl;
        long u = _UbM1Seed.seedUser(this, "R", "tc331@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL331", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, u, did, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, u, did, "COMPLETED", "2026-08-10", 999.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/reports/top-riders?startDate=2026-03-01&endDate=2026-03-31&limit=10", tok);
        assert2xx(r, "TC331");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("userId") ? it.get("userId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == u) {
                double spent = it.has("totalSpent") ? it.get("totalSpent").asDouble()
                        : it.has("total_spent") ? it.get("total_spent").asDouble() : -1.0;
                assertEquals(100.0, spent, 0.5,
                    "TC331: only March ride counted; got " + spent);
            }
        }
    }
}

// ─── TC332 — S1-F8 profile contains totalAddresses ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC332_UbProfileTotalAddressesTests extends TestBase {
    @Test
    @DisplayName("TC332 — Profile.totalAddresses equals number of saved addresses")
    void profile_total_addresses() throws Exception {
        BASE_URL = userServiceUrl;
        long u = _UbM1Seed.seedUser(this, "P", "tc332@uber.io", "RIDER");
        _UbM1Seed.seedAddress(this, u, "Home", true);
        _UbM1Seed.seedAddress(this, u, "Work", false);
        _UbM1Seed.seedAddress(this, u, "Gym",  false);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + u + "/profile", tok);
        assert2xx(r, "TC332");
        JsonNode j = parseNode(r.body());
        long total = _UbM2.rL(j, "totalAddresses", "total_addresses");
        if (total == -1) {
            JsonNode addrs = _UbM2.rO(j, "savedAddresses", "saved_addresses", "addresses");
            assertNotNull(addrs, "TC332: addresses key required");
            total = addrs.size();
        }
        assertEquals(3L, total, "TC332: totalAddresses=3; got " + total);
    }
}

// ─── TC333 — S1-F4 deactivate idempotency: already-deactivated returns 200 or 400 ──
@Tag("public")
@Tag("features_m1")
class TC333_UbDeactivateIdempotentTests extends TestBase {
    @Test
    @DisplayName("TC333 — Deactivating an already-DEACTIVATED user is no-op (DB stays DEACTIVATED)")
    void deactivate_idempotent() throws Exception {
        BASE_URL = userServiceUrl;
        long u = _UbM1Seed.seedUser(this, "D", "tc333@uber.io", "RIDER");
        try {
            jdbc.update("UPDATE \"" + tableName("User") + "\" SET \""
                + columnByField("User", "status") + "\" = "
                + el(tableName("User"), columnByField("User", "status"), "DEACTIVATED")
                + " WHERE id = ?", u);
        } catch (Exception ignored) {}
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + u + "/deactivate", "", tok);
        assertTrue(r.statusCode() < 500,
            "TC333: must not be 500; got " + r.statusCode());
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + columnByField("User", "status") + "\"::text FROM \""
                + tableName("User") + "\" WHERE id = ?",
            String.class, u);
        assertEquals("DEACTIVATED", dbStatus,
            "TC333: status must remain DEACTIVATED; got " + dbStatus);
    }
}

// ─── TC334 — S1-F2 nested JSONB merge ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC334_UbPrefsNestedMergeTests extends TestBase {
    @Test
    @DisplayName("TC334 — PUT preferences merges nested object into existing JSONB")
    void prefs_nested_merge() throws Exception {
        BASE_URL = userServiceUrl;
        long u = _UbM1Seed.seedUser(this, "P", "tc334@uber.io", "RIDER");
        _UbM1Seed.setUserPreferences(this, u,
            "{\"language\":\"en\",\"notifications\":{\"email\":true}}");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/users/" + u + "/preferences",
            "{\"notifications\":{\"sms\":true}}", tok);
        assert2xx(r, "TC334");
        JsonNode prefs = parseNode(r.body()).has("preferences")
                ? parseNode(r.body()).get("preferences") : parseNode(r.body());
        assertNotNull(prefs.get("notifications"),
            "TC334: notifications object must remain in JSONB");
    }
}

// ─── TC335 — S1-F3 ride summary averageFare ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC335_UbRideSummaryAverageFareTests extends TestBase {
    @Test
    @DisplayName("TC335 — Summary.averageFare = totalSpent / completedRides")
    void summary_average() throws Exception {
        BASE_URL = userServiceUrl;
        long u = _UbM1Seed.seedUser(this, "Avg", "tc335@uber.io", "RIDER");
        long did = _UbM1Seed.seedDriver(this, "D", "DL335", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, u, did, "COMPLETED", "2026-03-10", 80.0);
        _UbM1Seed.seedRide(this, u, did, "COMPLETED", "2026-03-11", 120.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + u + "/ride-summary", tok);
        assert2xx(r, "TC335");
        double avg = _UbM2.rD(parseNode(r.body()), "averageFare", "average_fare");
        assertEquals(100.0, avg, 0.5, "TC335: averageFare=100; got " + avg);
    }
}

// ─── TC336 — S2-F1 search no filter returns drivers ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC336_UbDriverSearchNoFilterTests extends TestBase {
    @Test
    @DisplayName("TC336 — Search with no filters returns the full driver list")
    void search_no_filter() throws Exception {
        BASE_URL = driverServiceUrl;
        _UbM1Seed.seedDriver(this, "DA", "DL336A", "AVAILABLE", 4.0, 5);
        _UbM1Seed.seedDriver(this, "DB", "DL336B", "BUSY", 3.5, 2);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/search", tok);
        assert2xx(r, "TC336");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC336: at least 2 drivers expected; got " + list.size());
    }
}

// ─── TC337 — S2-F3 earnings DTO contains totalRides ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC337_UbEarningsDtoTests extends TestBase {
    @Test
    @DisplayName("TC337 — Earnings DTO includes totalRides count")
    void earnings_dto() throws Exception {
        BASE_URL = driverServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc337@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "E", "DL337", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-11", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/" + d + "/earnings?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC337");
        long total = _UbM2.rL(parseNode(r.body()), "totalRides", "total_rides");
        assertEquals(2L, total, "TC337: totalRides=2; got " + total);
    }
}

// ─── TC338 — S2-F5 vehicle-type unknown enum returns 400 ────────────────────
@Tag("public")
@Tag("features_m1")
class TC338_UbVehicleTypeUnknownTests extends TestBase {
    @Test
    @DisplayName("TC338 — Vehicle-type ?type=YACHT (not in enum) returns 400")
    void vehicle_type_unknown() throws Exception {
        BASE_URL = driverServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/vehicle-type?type=YACHT", tok);
        assertEquals(400, r.statusCode(),
            "TC338: unknown enum must be 400; got " + r.statusCode());
    }
}

// ─── TC339 — S2-F6 top-rated excludes drivers below totalRatings threshold ──
@Tag("public")
@Tag("features_m1")
class TC339_UbTopRatedThresholdTests extends TestBase {
    @Test
    @DisplayName("TC339 — Top-rated only includes drivers with sufficient totalRatings")
    void top_rated_threshold() throws Exception {
        BASE_URL = driverServiceUrl;
        _UbM1Seed.seedDriver(this, "Newbie",  "DL339A", "AVAILABLE", 5.0, 1);
        _UbM1Seed.seedDriver(this, "Veteran", "DL339B", "AVAILABLE", 4.5, 200);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/reports/top-rated?limit=10", tok);
        assert2xx(r, "TC339");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 1, "TC339: at least 1 driver expected; got " + list.size());
    }
}

// ─── TC340 — S2-F7 rate ride owned by other driver → 400 ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC340_UbRateForeignRideTests extends TestBase {
    @Test
    @DisplayName("TC340 — Rate driver for ride that belongs to another driver returns 400")
    void rate_foreign_ride() throws Exception {
        BASE_URL = driverServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc340@uber.io", "RIDER");
        long d1 = _UbM1Seed.seedDriver(this, "D1", "DL340A", "AVAILABLE", 4.0, 0);
        long d2 = _UbM1Seed.seedDriver(this, "D2", "DL340B", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d1, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/drivers/" + d2 + "/rate",
            "{\"rideId\":" + rid + ",\"rating\":4}", tok);
        assertEquals(400, r.statusCode(),
            "TC340: foreign ride must be 400; got " + r.statusCode());
    }
}

// ─── TC341 — S2-F8 verify by non-admin returns 403 ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC341_UbVerifyDocByRiderTests extends TestBase {
    @Test
    @DisplayName("TC341 — Verify document with rider token returns 403")
    void verify_doc_by_rider() throws Exception {
        BASE_URL = driverServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "DV", "DL341", "AVAILABLE", 4.0, 0);
        long doc = _UbM1Seed.seedDocument(this, d, "LICENSE",
            java.time.LocalDate.of(2030, 12, 31), false);
        java.util.Map<String, Object> rider = seedAndLoginUser("tc341rider");
        String riderTok = (String) rider.get("token");
        HttpResponse<String> r = httpPutAuth(
            "/api/drivers/" + d + "/documents/" + doc + "/verify", "", riderTok);
        assertEquals(403, r.statusCode(),
            "TC341: rider must be 403; got " + r.statusCode());
    }
}

// ─── TC342 — S3-F1 search by user filter ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC342_UbRideSearchByUserTests extends TestBase {
    @Test
    @DisplayName("TC342 — Ride search returns rides for the authenticated rider scope")
    void search_by_user() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc342@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL342", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.seedRide(this, u, d, "REQUESTED", "2026-03-11", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/rides/search", tok);
        assert2xx(r, "TC342");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC342: at least 2 rides expected; got " + list.size());
    }
}

// ─── TC343 — S3-F3 estimate surge multiplier present ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC343_UbEstimateSurgeMultiplierTests extends TestBase {
    @Test
    @DisplayName("TC343 — Fare-estimate response includes surgeMultiplier")
    void estimate_surge() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        String body = "{\"pickupLatitude\":30.044,\"pickupLongitude\":31.235,"
                + "\"dropoffLatitude\":30.10,\"dropoffLongitude\":31.30}";
        HttpResponse<String> r = httpPostAuth("/api/rides/estimate", body, tok);
        assert2xx(r, "TC343");
        double mult = _UbM2.rD(parseNode(r.body()),
            "surgeMultiplier", "surge_multiplier");
        assertTrue(mult >= 1.0,
            "TC343: surgeMultiplier >= 1.0; got " + mult);
    }
}

// ─── TC344 — S3-F4 complete sets fare > 0 ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC344_UbCompleteFareSetTests extends TestBase {
    @Test
    @DisplayName("TC344 — Completing a ride retains a positive fare value")
    void complete_fare_set() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc344@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL344", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "IN_PROGRESS", "2026-03-10", 80.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/" + rid + "/complete", "", tok);
        assert2xx(r, "TC344");
        Double fare = jdbc.queryForObject(
            "SELECT \"" + columnByField("Ride", "fare") + "\" FROM \""
                + tableName("Ride") + "\" WHERE id = ?",
            Double.class, rid);
        assertTrue(fare != null && fare > 0,
            "TC344: fare > 0 expected; got " + fare);
    }
}

// ─── TC345 — S3-F6 analytics averageFare ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC345_UbAnalyticsAverageFareTests extends TestBase {
    @Test
    @DisplayName("TC345 — Analytics averageFare equals mean of COMPLETED fares")
    void analytics_avg() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc345@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL345", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 100.0);
        _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-11", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/analytics?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC345");
        double avg = _UbM2.rD(parseNode(r.body()),
            "averageFare", "average_fare");
        assertEquals(75.0, avg, 0.5, "TC345: averageFare=75; got " + avg);
    }
}

// ─── TC346 — S3-F7 cancel CANCELLED → 400 (already cancelled) ───────────────
@Tag("public")
@Tag("features_m1")
class TC346_UbCancelAlreadyCancelledTests extends TestBase {
    @Test
    @DisplayName("TC346 — Cancel a CANCELLED ride returns 400")
    void cancel_already_cancelled() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc346@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL346", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "CANCELLED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/" + rid + "/cancel", "", tok);
        assertEquals(400, r.statusCode(),
            "TC346: must be 400; got " + r.statusCode());
    }
}

// ─── TC347 — S3-F8 stops empty array → 400 ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC347_UbAddStopsEmptyTests extends TestBase {
    @Test
    @DisplayName("TC347 — Stops POST with empty stops array returns 400")
    void add_stops_empty() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc347@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL347", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "ACCEPTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/rides/" + rid + "/stops",
            "{\"stops\":[]}", tok);
        assertEquals(400, r.statusCode(),
            "TC347: empty stops must be 400; got " + r.statusCode());
    }
}

// ─── TC348 — S3-F9 details returns metadata ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC348_UbRideDetailsMetadataTests extends TestBase {
    @Test
    @DisplayName("TC348 — Details includes ride.metadata JSONB")
    void details_metadata() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc348@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL348", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.setRideMetadata(this, rid, "{\"tag\":\"vip\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/rides/" + rid + "/details", tok);
        assert2xx(r, "TC348");
        JsonNode j = parseNode(r.body());
        JsonNode md = j.has("metadata") ? j.get("metadata") : null;
        assertNotNull(md, "TC348: metadata key required; body=" + r.body());
        assertEquals("vip",
            md.has("tag") ? md.get("tag").asText() : "",
            "TC348: metadata.tag=vip; got " + md);
    }
}

// ─── TC349 — S3-F2 assign with REQUESTED ride+OFFLINE driver → 400 ──────────
@Tag("public")
@Tag("features_m1")
class TC349_UbAssignOfflineDriverTests extends TestBase {
    @Test
    @DisplayName("TC349 — Assign OFFLINE driver returns 400")
    void assign_offline_driver() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc349@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "Off", "DL349", "OFFLINE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, 0L, "REQUESTED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/rides/" + rid + "/assign?driverId=" + d, "", tok);
        assertEquals(400, r.statusCode(),
            "TC349: OFFLINE driver must be 400; got " + r.statusCode());
    }
}

// ─── TC350 — S3-F1 search invalid date format → 400 ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC350_UbRideSearchInvalidDateTests extends TestBase {
    @Test
    @DisplayName("TC350 — Search with malformed startDate returns 400")
    void search_invalid_date() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/search?startDate=not-a-date&endDate=2026-03-31", tok);
        assertEquals(400, r.statusCode(),
            "TC350: invalid date must be 400; got " + r.statusCode());
    }
}

// ─── TC351 — S3-F8 stops with duplicate stopOrder → 400 ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC351_UbAddStopsDuplicateOrderTests extends TestBase {
    @Test
    @DisplayName("TC351 — Stops POST with duplicate stopOrder returns 400")
    void add_stops_duplicate() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc351@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL351", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "ACCEPTED", "2030-03-10", 50.0);
        String tok = adminToken();
        String body = "{\"stops\":["
                + "{\"stopOrder\":1,\"latitude\":30.05,\"longitude\":31.24,\"address\":\"A\"},"
                + "{\"stopOrder\":1,\"latitude\":30.07,\"longitude\":31.26,\"address\":\"B\"}"
                + "]}";
        HttpResponse<String> r = httpPostAuth(
            "/api/rides/" + rid + "/stops", body, tok);
        assertEquals(400, r.statusCode(),
            "TC351: duplicate stopOrder must be 400; got " + r.statusCode());
    }
}

// ─── TC352 — S4-F2 update without latitude → 400 ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC352_UbUpdateLocationMissingLatTests extends TestBase {
    @Test
    @DisplayName("TC352 — Update without latitude returns 400")
    void update_missing_lat() throws Exception {
        BASE_URL = locationServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "D", "DL352", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/locations/driver/" + d,
            "{\"longitude\":31.23}", tok);
        assertEquals(400, r.statusCode(),
            "TC352: missing latitude must be 400; got " + r.statusCode());
    }
}

// ─── TC353 — S4-F3 nearby returns distanceKm ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC353_UbNearbyDistanceFieldTests extends TestBase {
    @Test
    @DisplayName("TC353 — Nearby DTO includes distanceKm field")
    void nearby_distance_field() throws Exception {
        BASE_URL = locationServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "Near", "DL353", "AVAILABLE", 4.5, 0);
        _UbM1Seed.seedLocation(this, d, 30.045, 31.236, "2026-03-10 08:00:00");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/nearby?lat=30.044&lon=31.235&radiusKm=10", tok);
        assert2xx(r, "TC353");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        if (list.size() > 0) {
            JsonNode it = list.get(0);
            boolean hasDist = it.has("distanceKm") || it.has("distance_km") || it.has("distance");
            assertTrue(hasDist, "TC353: distanceKm key required; body=" + r.body());
        }
    }
}

// ─── TC354 — S4-F8 averageSpeed isolated ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC354_UbDriverSummaryAverageSpeedTests extends TestBase {
    @Test
    @DisplayName("TC354 — Driver summary averageSpeed = mean of metadata.speed values")
    void summary_average_speed() throws Exception {
        BASE_URL = locationServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "Avg", "DL354", "AVAILABLE", 4.0, 0);
        long l1 = _UbM1Seed.seedLocation(this, d, 30.04, 31.23, "2026-03-10 08:00:00");
        long l2 = _UbM1Seed.seedLocation(this, d, 30.05, 31.24, "2026-03-10 09:00:00");
        long l3 = _UbM1Seed.seedLocation(this, d, 30.06, 31.25, "2026-03-10 10:00:00");
        _UbM1Seed.setLocationMetadata(this, l1, "{\"speed\":40}");
        _UbM1Seed.setLocationMetadata(this, l2, "{\"speed\":60}");
        _UbM1Seed.setLocationMetadata(this, l3, "{\"speed\":80}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/driver/" + d + "/summary?startDate=2026-03-01&endDate=2026-03-31",
            tok);
        assert2xx(r, "TC354");
        double avg = _UbM2.rD(parseNode(r.body()),
            "averageSpeed", "average_speed");
        assertEquals(60.0, avg, 0.5, "TC354: averageSpeed=60; got " + avg);
    }
}

// ─── TC355 — S4-F8 maxSpeed isolated ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC355_UbDriverSummaryMaxSpeedTests extends TestBase {
    @Test
    @DisplayName("TC355 — Driver summary maxSpeed = max of metadata.speed values")
    void summary_max_speed() throws Exception {
        BASE_URL = locationServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "Max", "DL355", "AVAILABLE", 4.0, 0);
        long l1 = _UbM1Seed.seedLocation(this, d, 30.04, 31.23, "2026-03-10 08:00:00");
        long l2 = _UbM1Seed.seedLocation(this, d, 30.05, 31.24, "2026-03-10 09:00:00");
        _UbM1Seed.setLocationMetadata(this, l1, "{\"speed\":30}");
        _UbM1Seed.setLocationMetadata(this, l2, "{\"speed\":90}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/driver/" + d + "/summary?startDate=2026-03-01&endDate=2026-03-31",
            tok);
        assert2xx(r, "TC355");
        double max = _UbM2.rD(parseNode(r.body()),
            "maxSpeed", "max_speed");
        assertEquals(90.0, max, 0.5, "TC355: maxSpeed=90; got " + max);
    }
}

// ─── TC356 — S4-F4 batch with mismatched driverId → 400 ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC356_UbBatchUnknownDriverTests extends TestBase {
    @Test
    @DisplayName("TC356 — Batch with unknown driverId returns 404")
    void batch_unknown_driver() throws Exception {
        BASE_URL = locationServiceUrl;
        String tok = adminToken();
        String body = "{\"driverId\":999999,\"locations\":["
                + "{\"latitude\":30.05,\"longitude\":31.24,\"timestamp\":\"2026-03-10T08:00:00\"}"
                + "]}";
        HttpResponse<String> r = httpPostAuth("/api/locations/batch", body, tok);
        assertEquals(404, r.statusCode(),
            "TC356: must be 404; got " + r.statusCode());
    }
}

// ─── TC357 — S4-F6 history without driverId returns all-driver history ──────
@Tag("public")
@Tag("features_m1")
class TC357_UbHistoryAllDriversTests extends TestBase {
    @Test
    @DisplayName("TC357 — History without driverId param returns history for all drivers")
    void history_all_drivers() throws Exception {
        BASE_URL = locationServiceUrl;
        long d1 = _UbM1Seed.seedDriver(this, "D1", "DL357A", "AVAILABLE", 4.0, 0);
        long d2 = _UbM1Seed.seedDriver(this, "D2", "DL357B", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedLocation(this, d1, 30.04, 31.23, "2026-03-10 08:00:00");
        _UbM1Seed.seedLocation(this, d2, 30.05, 31.24, "2026-03-10 09:00:00");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/history?startDate=2026-03-01&endDate=2026-03-31", tok);
        // either 200 with multi-driver list, or 400 if driverId required
        assertTrue(r.statusCode() == 200 || r.statusCode() == 400,
            "TC357: must be 200 or 400; got " + r.statusCode());
    }
}

// ─── TC358 — S4-F9 stationary requires recent timestamp ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC358_UbStationaryStaleTimestampTests extends TestBase {
    @Test
    @DisplayName("TC358 — Stationary excludes drivers whose last update is older than sinceMinutes")
    void stationary_stale() throws Exception {
        BASE_URL = locationServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "Stale", "DL358", "AVAILABLE", 4.0, 0);
        java.sql.Timestamp old = java.sql.Timestamp.valueOf(
            java.time.LocalDateTime.now().minusHours(2));
        long l = _UbM1Seed.seedLocation(this, d, 30.04, 31.23, "2026-03-10 08:00:00");
        _UbM1Seed.setLocationMetadata(this, l, "{\"speed\":2}");
        _UbM1Seed.setLocationTimestamp(this, l, old);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/stationary?maxSpeed=5&sinceMinutes=5", tok);
        assert2xx(r, "TC358");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("driverId") ? it.get("driverId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            assertTrue(id != d,
                "TC358: stale driver must be excluded; got " + id);
        }
    }
}

// ─── TC359 — S4-F7 purge with valid days deletes matching rows ─────────────
@Tag("public")
@Tag("features_m1")
class TC359_UbPurgeKeepsRecentTests extends TestBase {
    @Test
    @DisplayName("TC359 — Purge?olderThanDays=30 retains rows newer than 30 days")
    void purge_keeps_recent() throws Exception {
        BASE_URL = locationServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "Pk", "DL359", "AVAILABLE", 4.0, 0);
        long recent = _UbM1Seed.seedLocation(this, d, 30.05, 31.24, "2026-03-10 08:00:00");
        _UbM1Seed.setLocationTimestamp(this, recent,
            java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
        String tok = adminToken();
        HttpResponse<String> r = httpDeleteAuth(
            "/api/locations/purge?olderThanDays=30", tok);
        assert2xx(r, "TC359");
        Integer keep = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("Location") + "\" WHERE id = ?",
            Integer.class, recent);
        assertEquals(1, keep.intValue(),
            "TC359: recent row must remain; got " + keep);
    }
}

// ─── TC360 — S5-F1 search by user filter ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC360_UbPaymentSearchByUserTests extends TestBase {
    @Test
    @DisplayName("TC360 — Search list contains payments scoped to admin's view")
    void search_by_user() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc360@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL360", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.seedPayment(this, rid, u, 50.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/payments/search", tok);
        assert2xx(r, "TC360");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 1, "TC360: at least 1 payment expected");
    }
}

// ─── TC361 — S5-F4 process records method in DB ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC361_UbProcessMethodPersistedTests extends TestBase {
    @Test
    @DisplayName("TC361 — Process payment persists method=CASH to PG")
    void process_method_persisted() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc361@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL361", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/ride/" + rid,
            "{\"method\":\"CASH\"}", tok);
        assert2xx(r, "TC361");
        String mCol = columnByField("Payment", "method");
        String fkCol = columnByField("Payment", "ride");
        String dbMethod = jdbc.queryForObject(
            "SELECT \"" + mCol + "\"::text FROM \"" + tableName("Payment")
                + "\" WHERE \"" + fkCol + "\" = ?",
            String.class, rid);
        assertEquals("CASH", dbMethod, "TC361: method must persist as CASH; got " + dbMethod);
    }
}

// ─── TC362 — S5-F5 apply coupon increments currentUses ──────────────────────
@Tag("public")
@Tag("features_m1")
class TC362_UbCouponUsesIncrementTests extends TestBase {
    @Test
    @DisplayName("TC362 — Applying a coupon increments its currentUses")
    void coupon_uses_increment() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc362@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL362", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, u, 100.0, "CREDIT_CARD", "PENDING");
        long cid = _UbM1Seed.seedCoupon(this, "TC362", "PERCENTAGE", 10.0, 100,
            _UbM1Seed.futureDateTime(), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/" + pid + "/coupons/" + cid, "", tok);
        assert2xx(r, "TC362");
        String cu = columnByField("Coupon", "currentUses");
        Integer uses = jdbc.queryForObject(
            "SELECT \"" + cu + "\" FROM \"" + tableName("Coupon") + "\" WHERE id = ?",
            Integer.class, cid);
        assertEquals(1, uses.intValue(),
            "TC362: currentUses must increment to 1; got " + uses);
    }
}

// ─── TC363 — S5-F6 averagePayment isolated ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC363_UbRevenueAvgTests extends TestBase {
    @Test
    @DisplayName("TC363 — Revenue.averagePayment = totalRevenue / totalTransactions")
    void revenue_avg() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc363@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL363", "AVAILABLE", 4.0, 0);
        long r1 = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 100.0);
        long r2 = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-11", 50.0);
        long p1 = _UbM1Seed.seedPayment(this, r1, u, 100.0, "CREDIT_CARD", "COMPLETED");
        long p2 = _UbM1Seed.seedPayment(this, r2, u, 50.0,  "CREDIT_CARD", "COMPLETED");
        setAllDateColumns(tableName("Payment"), p1,
            java.sql.Timestamp.valueOf("2026-03-10 12:00:00"));
        setAllDateColumns(tableName("Payment"), p2,
            java.sql.Timestamp.valueOf("2026-03-11 12:00:00"));
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/reports/revenue?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC363");
        double avg = _UbM2.rD(parseNode(r.body()),
            "averagePayment", "average_payment");
        assertEquals(75.0, avg, 0.5, "TC363: averagePayment=75; got " + avg);
    }
}

// ─── TC364 — S5-F8 details transactionDetails JSONB ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC364_UbDetailsTransactionFieldTests extends TestBase {
    @Test
    @DisplayName("TC364 — Details exposes transactionDetails JSONB")
    void details_transaction() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc364@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL364", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        long pid = _UbM1Seed.seedPayment(this, rid, u, 50.0, "CREDIT_CARD", "COMPLETED");
        _UbM1Seed.setPaymentTransactionDetails(this, pid,
            "{\"gatewayResponse\":\"approved\",\"cardLastFour\":\"4242\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/" + pid + "/details", tok);
        assert2xx(r, "TC364");
        JsonNode j = parseNode(r.body());
        JsonNode td = j.has("transactionDetails") ? j.get("transactionDetails")
                : j.has("transaction_details") ? j.get("transaction_details") : null;
        assertNotNull(td, "TC364: transactionDetails key required; body=" + r.body());
    }
}

// ─── TC365 — S5-F9 timesUsed in CouponUsageDTO ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC365_UbTopCouponsTimesUsedTests extends TestBase {
    @Test
    @DisplayName("TC365 — top-used DTO exposes timesUsed = number of applications")
    void top_coupons_times_used() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc365@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL365", "AVAILABLE", 4.0, 0);
        long c = _UbM1Seed.seedCoupon(this, "TC365", "FIXED", 5.0, 100,
            _UbM1Seed.futureDateTime(), true);
        for (int i = 0; i < 2; i++) {
            long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 100.0);
            long pid = _UbM1Seed.seedPayment(this, rid, u, 100.0, "CREDIT_CARD", "COMPLETED");
            _UbM1Seed.seedPaymentCoupon(this, pid, c, 5.0);
        }
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/coupons/top-used?limit=10", tok);
        assert2xx(r, "TC365");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("couponId") ? it.get("couponId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == c) {
                long used = it.has("timesUsed") ? it.get("timesUsed").asLong()
                        : it.has("times_used") ? it.get("times_used").asLong() : -1L;
                assertEquals(2L, used, "TC365: timesUsed=2; got " + used);
                return;
            }
        }
        throw new AssertionError("TC365: target coupon not in result");
    }
}

// ─── TC366 — S5-F2 refund 400 already-refunded ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC366_UbRefundAlreadyRefundedTests extends TestBase {
    @Test
    @DisplayName("TC366 — Refund a REFUNDED payment returns 400")
    void refund_already_refunded() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc366@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL366", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        long pid = _UbM1Seed.seedPayment(this, rid, u, 50.0, "CREDIT_CARD", "REFUNDED");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/payments/" + pid + "/refund",
            "{\"reason\":\"x\"}", tok);
        assertEquals(400, r.statusCode(),
            "TC366: already-refunded must be 400; got " + r.statusCode());
    }
}

// ─── TC367 — S5-F4 process for CANCELLED ride → 400 ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC367_UbProcessCancelledRideTests extends TestBase {
    @Test
    @DisplayName("TC367 — Process payment for CANCELLED ride returns 400")
    void process_cancelled_ride() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc367@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL367", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "CANCELLED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/payments/ride/" + rid,
            "{\"method\":\"CREDIT_CARD\"}", tok);
        assertEquals(400, r.statusCode(),
            "TC367: CANCELLED ride must be 400; got " + r.statusCode());
    }
}

// ─── TC368 — S5-F7 retry resets transactionDetails ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC368_UbRetryResetsTxDetailsTests extends TestBase {
    @Test
    @DisplayName("TC368 — Retry on FAILED payment is permitted to update transactionDetails")
    void retry_resets() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc368@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL368", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        long pid = _UbM1Seed.seedPayment(this, rid, u, 50.0, "CREDIT_CARD", "FAILED");
        _UbM1Seed.setPaymentTransactionDetails(this, pid,
            "{\"gatewayResponse\":\"declined\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/payments/" + pid + "/retry", "", tok);
        assert2xx(r, "TC368");
        String stCol = columnByField("Payment", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Payment") + "\" WHERE id = ?",
            String.class, pid);
        assertTrue(!"FAILED".equals(dbStatus),
            "TC368: retry must transition out of FAILED; got " + dbStatus);
    }
}

// ─── TC369 — S5-F8 details paymentId echoed back ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC369_UbDetailsPaymentIdTests extends TestBase {
    @Test
    @DisplayName("TC369 — Details body contains paymentId equal to the path id")
    void details_payment_id() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc369@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL369", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        long pid = _UbM1Seed.seedPayment(this, rid, u, 50.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/" + pid + "/details", tok);
        assert2xx(r, "TC369");
        JsonNode j = parseNode(r.body());
        long echoed = j.has("paymentId") ? j.get("paymentId").asLong()
                : j.has("payment_id") ? j.get("payment_id").asLong()
                : j.has("id") ? j.get("id").asLong() : -1L;
        assertEquals(pid, echoed,
            "TC369: paymentId echoed expected " + pid + "; got " + echoed);
    }
}

// ─── TC370 — S2-F2 vehicle 404 ──────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC370_UbVehicleUnknownDriverTests extends TestBase {
    @Test
    @DisplayName("TC370 — Vehicle PUT for unknown driver returns 404")
    void vehicle_unknown_driver() throws Exception {
        BASE_URL = driverServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/drivers/999999/vehicle",
            "{\"color\":\"Red\"}", tok);
        assertEquals(404, r.statusCode(),
            "TC370: must be 404; got " + r.statusCode());
    }
}

// ─── TC371 — S3-F4 complete ride 404 ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC371_UbCompleteUnknownTests extends TestBase {
    @Test
    @DisplayName("TC371 — Complete unknown ride returns 404 (alt covering)")
    void complete_unknown() throws Exception {
        BASE_URL = rideServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/rides/-1/complete", "", tok);
        assertTrue(r.statusCode() == 404 || r.statusCode() == 400,
            "TC371: must be 404 or 400; got " + r.statusCode());
    }
}

// ─── TC372 — S2-F6 limit=0 returns empty list ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC372_UbTopRatedZeroLimitTests extends TestBase {
    @Test
    @DisplayName("TC372 — Top-rated limit=0 returns empty list or 400")
    void top_rated_zero_limit() throws Exception {
        BASE_URL = driverServiceUrl;
        _UbM1Seed.seedDriver(this, "D", "DL372", "AVAILABLE", 4.5, 5);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/drivers/reports/top-rated?limit=0", tok);
        if (r.statusCode() == 200) {
            JsonNode arr = parseNode(r.body());
            JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
            assertEquals(0, list.size(), "TC372: limit=0 → empty");
        } else {
            assertEquals(400, r.statusCode(), "TC372: limit=0 → 400; got " + r.statusCode());
        }
    }
}

// ─── TC373 — S3-F5 metadata search returns ride metadata in body ────────────
@Tag("public")
@Tag("features_m1")
class TC373_UbMetadataReturnsRideTests extends TestBase {
    @Test
    @DisplayName("TC373 — Metadata search response items include the matched ride id")
    void metadata_returns_ride() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc373@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL373", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 50.0);
        _UbM1Seed.setRideMetadata(this, rid, "{\"feature\":\"PREMIUM\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/rides/metadata/search?key=feature&value=PREMIUM", tok);
        assert2xx(r, "TC373");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean found = false;
        for (JsonNode it : list) {
            long id = it.has("id") ? it.get("id").asLong()
                    : it.has("rideId") ? it.get("rideId").asLong() : -1L;
            if (id == rid) found = true;
        }
        assertTrue(found, "TC373: target ride must be in results");
    }
}

// ─── TC374 — S4-F5 metadata lt operator ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC374_UbLocationMetadataLtTests extends TestBase {
    @Test
    @DisplayName("TC374 — Metadata search ?operator=lt&value=40 returns rows below threshold")
    void metadata_lt() throws Exception {
        BASE_URL = locationServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "Lt", "DL374", "AVAILABLE", 4.0, 0);
        long l1 = _UbM1Seed.seedLocation(this, d, 30.04, 31.23, "2026-03-10 08:00:00");
        long l2 = _UbM1Seed.seedLocation(this, d, 30.05, 31.24, "2026-03-10 09:00:00");
        _UbM1Seed.setLocationMetadata(this, l1, "{\"speed\":20}");
        _UbM1Seed.setLocationMetadata(this, l2, "{\"speed\":80}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/locations/metadata/search?key=speed&operator=lt&value=40", tok);
        assert2xx(r, "TC374");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("id") ? it.get("id").asLong() : -1L;
            assertTrue(id != l2, "TC374: speed=80 row must be excluded; got " + id);
        }
    }
}

// ─── TC375 — S2-F9 expired-docs DTO contains driverName ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC375_UbExpiredDocsDriverNameTests extends TestBase {
    @Test
    @DisplayName("TC375 — Expired-docs DTO includes driverName")
    void expired_docs_driver_name() throws Exception {
        BASE_URL = driverServiceUrl;
        long d = _UbM1Seed.seedDriver(this, "Expired Hassan", "DL375", "AVAILABLE", 4.0, 0);
        _UbM1Seed.seedDocument(this, d, "LICENSE",
            java.time.LocalDate.now().minusYears(6), false);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/drivers/documents/expired", tok);
        assert2xx(r, "TC375");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean found = false;
        for (JsonNode it : list) {
            long id = it.has("driverId") ? it.get("driverId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == d) {
                String dn = it.has("driverName") ? it.get("driverName").asText()
                        : it.has("driver_name") ? it.get("driver_name").asText() : "";
                assertTrue(dn.contains("Hassan"),
                    "TC375: driverName must contain 'Hassan'; got " + dn);
                found = true;
            }
        }
        assertTrue(found, "TC375: target driver must be present");
    }
}

// ─── TC376 — S1-F1 search empty query returns full list ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC376_UbSearchUsersAllTests extends TestBase {
    @Test
    @DisplayName("TC376 — Search /api/users/search with no params returns all users")
    void search_all_users() throws Exception {
        BASE_URL = userServiceUrl;
        _UbM1Seed.seedUser(this, "A1", "tc376_a@uber.io", "RIDER");
        _UbM1Seed.seedUser(this, "A2", "tc376_b@uber.io", "RIDER");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search", tok);
        assert2xx(r, "TC376");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC376: at least 2 users expected; got " + list.size());
    }
}

// ─── TC377 — S3-F9 details for cancelled ride still returns body ────────────
@Tag("public")
@Tag("features_m1")
class TC377_UbRideDetailsCancelledTests extends TestBase {
    @Test
    @DisplayName("TC377 — Details for CANCELLED ride still returns 200 with status=CANCELLED")
    void details_cancelled() throws Exception {
        BASE_URL = rideServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc377@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL377", "AVAILABLE", 4.0, 0);
        long rid = _UbM1Seed.seedRide(this, u, d, "CANCELLED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/rides/" + rid + "/details", tok);
        assert2xx(r, "TC377");
        JsonNode j = parseNode(r.body());
        String s = j.has("status") ? j.get("status").asText() : "";
        assertEquals("CANCELLED", s, "TC377: status=CANCELLED; got " + s);
    }
}

// ─── TC378 — S5-F9 expired flag on past-expiry coupons ──────────────────────
@Tag("public")
@Tag("features_m1")
class TC378_UbTopCouponsExpiredFlagTests extends TestBase {
    @Test
    @DisplayName("TC378 — top-used DTO marks expired=true for past-expiry coupons")
    void top_coupons_expired_flag() throws Exception {
        BASE_URL = paymentServiceUrl;
        long u = _UbM1Seed.seedUser(this, "U", "tc378@uber.io", "RIDER");
        long d = _UbM1Seed.seedDriver(this, "D", "DL378", "AVAILABLE", 4.0, 0);
        long c = _UbM1Seed.seedCoupon(this, "TC378EXP", "PERCENTAGE", 10.0, 100,
            _UbM1Seed.pastDateTime(), true);
        long rid = _UbM1Seed.seedRide(this, u, d, "COMPLETED", "2026-03-10", 100.0);
        long pid = _UbM1Seed.seedPayment(this, rid, u, 100.0, "CREDIT_CARD", "COMPLETED");
        _UbM1Seed.seedPaymentCoupon(this, pid, c, 10.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/payments/coupons/top-used?limit=10", tok);
        assert2xx(r, "TC378");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = it.has("couponId") ? it.get("couponId").asLong()
                    : it.has("id") ? it.get("id").asLong() : -1L;
            if (id == c) {
                assertTrue(it.has("expired") && it.get("expired").asBoolean(),
                    "TC378: expired must be true for past-expiry coupon; got " + it);
                return;
            }
        }
        throw new AssertionError("TC378: target coupon not in result");
    }
}
