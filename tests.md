# Auto Payload Positioner - Regression Tests

These tests are meant to be copy/paste friendly for Burp Repeater (then right-click -> run Auto Payload Positioner -> verify Intruder insertion points).

Notes:
- Montoya does not allow zero-length insertion points. This extension inserts the placeholder `__DELETE_ME__` into empty values so you can fuzz them.
- When placeholders are applied, the request sent to Intruder is intentionally modified (you will see `__DELETE_ME__`).
- Expected insertion points below are the *minimum required*. Additional insertion points may appear depending on headers/URL mode selected.

---

## JSON Tests

### J1 Flat JSON
Body:
```json
{"username":"alice","age":21,"active":true}
```
Expected insertion points include:
- `alice`
- `21`
- `true`

### J2 Nested JSON
Body:
```json
{"user":{"profile":{"displayName":"Alice","email":"a@example.com"}}}
```
Expected insertion points include:
- `Alice`
- `a@example.com`

### J3 Array JSON (looped objects)
Body:
```json
{"items":[{"id":1,"name":"one"},{"id":2,"name":"two"}]}
```
Expected insertion points include:
- `1`
- `one`
- `2`
- `two`

### J4 Repeated values (must not collapse to first match)
Body:
```json
{"a":"same","b":"same","c":["same","same"]}
```
Expected insertion points include:
- `same` (4 separate insertion points total)

### J5 Embedded JSON string (object)
Body:
```json
{"embedded":"{\"json\":\"thisisembedded\",\"key\":1}"}
```
Expected insertion points include:
- `thisisembedded`
- `1`

### J6 Array of embedded JSON strings
Body:
```json
{"rows":["{\"id\":1,\"name\":\"one\"}","{\"id\":2,\"name\":\"two\"}"]}
```
Expected insertion points include:
- `1`
- `one`
- `2`
- `two`

### J7 JSON-in-key (embedded JSON inside a JSON key)
Body:
```json
{"{\"type\":\"user\",\"id\":123}":"value","normal":"ok"}
```
Expected insertion points include:
- `user`
- `123`
- `value`
- `ok`

### J8 Real-world style sample (embedded field inside nested arrays/objects)
Body:
```json
{"stats":[{"longTasks":[{"name":"unknown","embedded":"{\"json\":\"thisisembedded\",\"key\":1}","duration":246,"url":"https://github.com/"}],"timestamp":1771650231826,"loggedIn":false,"staff":false,"bundler":"rspack","ui":false,"app":"landing-pages","ssr":"true"}]}
```
Expected insertion points include:
- `unknown`
- `thisisembedded`
- `1`
- `246`
- `https://github.com/`
- `1771650231826`
- `false` (multiple positions may exist)
- `rspack`
- `landing-pages`
- `true`

---

## Empty-Value Placeholder Tests (`__DELETE_ME__`)

### E1 Plain key=value body with empty value
Request:
```http
POST /test/empty HTTP/1.1
Host: example.com
Content-Type: text/plain

data=&x=1
```
Expected behavior:
- Request sent to Intruder contains: `data=__DELETE_ME__&x=1`
Expected insertion points include:
- `__DELETE_ME__`
- `1`

### E2 Query string empty value
Request:
```http
GET /search?q=&page=2 HTTP/1.1
Host: example.com
```
Expected behavior:
- Request sent to Intruder contains `q=__DELETE_ME__`
Expected insertion points include:
- `__DELETE_ME__`
- `2`

### E3 JSON empty string value
Request:
```http
POST /test/json-empty HTTP/1.1
Host: example.com
Content-Type: application/json

{"data":"","ok":"yes"}
```
Expected behavior:
- Request sent to Intruder contains `"data":"__DELETE_ME__"`
Expected insertion points include:
- `__DELETE_ME__`
- `yes`

---

## XML Tests

### X1 XML tag text + attributes
Body:
```xml
<root><user id="42" role="admin">alice</user><meta token="abc123"/></root>
```
Expected insertion points include:
- `42`
- `admin`
- `alice`
- `abc123`

---

## Mixed / Plain Text Tests

### M1 text/plain with embedded JSON object
Request:
```http
POST /test/mixed-json HTTP/1.1
Host: example.com
Content-Type: text/plain

prefix=ok payload={"name":"alice","age":21,"active":true} suffix=end
```
Expected insertion points include:
- `ok`
- `alice`
- `21`
- `true`
- `end`

### M2 text/plain with embedded XML
Request:
```http
POST /test/mixed-xml HTTP/1.1
Host: example.com
Content-Type: text/plain

log=<event type="click">buttonA</event>&status=done
```
Expected insertion points include:
- `click`
- `buttonA`
- `done`

### M3 key=value pairs (non-JSON, non-XML)
Request:
```http
POST /test/kv HTTP/1.1
Host: example.com
Content-Type: text/plain

a=1&b=hello-world&c=true&token=xyz123
```
Expected insertion points include:
- `1`
- `hello-world`
- `true`
- `xyz123`

---

## Parameter Parsing Tests (Burp parser)

### P1 application/x-www-form-urlencoded
Request:
```http
POST /test/form HTTP/1.1
Host: example.com
Content-Type: application/x-www-form-urlencoded

username=alice&password=secret123&remember=true
```
Expected insertion points include:
- `alice`
- `secret123`
- `true`

### P2 Query params on GET
Request:
```http
GET /search?q=payload&page=2&sort=desc HTTP/1.1
Host: example.com
```
Expected insertion points include:
- `payload`
- `2`
- `desc`

---

## Header Tests

### H1 Cookie + Authorization parsing
Request:
```http
GET /api HTTP/1.1
Host: example.com
Authorization: Bearer token-part-123
Cookie: sid=abc123; theme=dark; locale=en-US
X-Custom: hello-header

```
Expected insertion points include:
- `token-part-123`
- `abc123`
- `dark`
- `en-US`
- `hello-header`

