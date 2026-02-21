<div align="center">

# Auto Payload Positioner
#### A Pentester’s handy tool for Automated Insertion Point Placement

⚡ Fully Automated, Quick, Handy - Intruder Payload Positioner so you don't have to! 

![GitHub contributors Auto Payload Positioner](https://img.shields.io/github/contributors/netsquare/Auto-Payload-Positioner)
![GitHub all releases](https://img.shields.io/github/downloads/netsquare/Auto-Payload-Positioner/total)
![GitHub release (latest by SemVer)](https://img.shields.io/github/downloads/netsquare/Auto-Payload-Positioner/latest/total)
![Latest release](https://img.shields.io/github/release/netsquare/Auto-Payload-Positioner.svg)
![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)
[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

</div>

![Cyberpunk Auto Payload Positioner Overview](https://github.com/user-attachments/assets/cac135ef-4b88-4bef-9695-8785561639a2)
 Image generated using AI tools.

---

## What is Auto Payload Positioner?

A Burp Suite extension built on the Montoya API that automatically detects and marks “interesting” insertion points throughout an HTTP request. Rather than manually highlighting each location (headers, parameters, JSON/XML/form bodies, etc.), this extension sets payload positions for you—so you can focus on testing vulnerabilities, not on placement.

Watch the demo!

[https://github.com/user-attachments/assets/d50251b8-6b1c-4341-b18e-ae54eb24a847](https://github.com/user-attachments/assets/d081004c-6416-43f1-b66c-ddd6c072ce07
)

## Features

- Automatic detection of insertion points

  - HTTP method (GET, POST, etc.)

  - Last path segment of the URL

  - URL query parameters

  - Form data parameters
  
  - Header values (Cookie, Authorization, and all other headers)

  - JSON bodies (values, nested objects/arrays, repeated values)

  - Embedded JSON inside JSON strings (JSON-in-string)

  - XML bodies (tag content, attributes)

  - Embedded formats and key=value pairs in text/plain or mixed bodies

- Context-menu integration

  - Right-click on any request in Proxy, Target, Intruder, or Repeater

  - Select Set positions to auto-mark insertion points

- Intruder-ready

  - Builds an HttpRequestTemplate with all detected ranges

  - Sends the request directly to Intruder with your custom positions

- Robust range validation

  - Filters out invalid, overlapping, or out-of-bounds ranges

  - Ensures stable behavior even on complex requests


## 🛠️ Getting Started 

## 1. Downlaod from Releases: https://github.com/netsquare/Auto-Payload-Positioner/releases

## 2. Installation

https://github.com/user-attachments/assets/30e1e4a9-6105-4e89-8d4a-3e125360dfc4

  - Load into Burp

  - Open Burp Suite → Extensions.

  - Click Add.

  - Choose Java as the extension type.

  - Select your downloaded JAR.

  - Confirm & ensure Auto Payload Positioner appears in the list.

  - Verify, in the Extender output tab, look for: `Auto Payload Positioner loaded Successfully!`

## 3. Usage

[https://github.com/user-attachments/assets/d50251b8-6b1c-4341-b18e-ae54eb24a847](https://github.com/user-attachments/assets/d081004c-6416-43f1-b66c-ddd6c072ce07
)

- Send or capture a request in Proxy, Repeater, Intruder, or Target.

- Right-click anywhere on the request.

- Select `Set positions` from the context menu.

- The extension will:

  - Scan the request for all “interesting” parts

  - Automatically highlight each range with Intruder markers (§)

  - Send the templated request to Intruder

- Switch to Intruder, review the auto-populated payload positions, and launch your attack.

## Embedded JSON and Empty Values

### Embedded JSON (JSON inside a JSON string)

If a JSON field contains an escaped JSON document, the extension will place insertion points inside the embedded JSON too.

Example:
```json
{"embedded":"{\"json\":\"thisisembedded\",\"key\":1}"}
```

Expected insertion points include:
- `thisisembedded`
- `1`

### Empty values and `__DELETE_ME__` placeholder

Newer Burp Montoya API versions do not allow zero-length insertion points. To support empty values like `data=` or `{\"data\":\"\"}`, the extension inserts a placeholder marker so Intruder has a valid non-zero range to target.

Example (plain/form/query-style):
- Input: `data=&x=1`
- Sent to Intruder as: `data=__DELETE_ME__&x=1`

Example (JSON empty string):
```json
{"data":"","ok":"yes"}
```
Sent to Intruder as:
```json
{"data":"__DELETE_ME__","ok":"yes"}
```

Tip: `__DELETE_ME__` is intentionally obvious so you can remove it after targeting it.

## Building from Source

```bash
# Clone the repo
git clone https://github.com/netsquare/Auto-Payload-Positioner.git
cd Auto-Payload-Positioner

# (Maven)
mvn clean package

The built JAR will be in target/ 

Load that JAR in Burp as described above.
```

## Testing / Regression Checklist

See `tests.md` for copy/paste test cases and expected insertion points (JSON, embedded JSON, XML, key=value, `__DELETE_ME__`).

## To report bugs, issues, feature suggestion, Performance issue, general question, Documentation issue.
 - Kindly open an issue with respective template.

## Acknowledgment

This project is an extension for [Burpsuite](https://portswigger.net/burp) and leverages [Montoya API](https://github.com/PortSwigger/burp-extensions-montoya-api)

## 📄 License

Auto-Payload-Positioner and all related projects inherits the Apache 2.0, see [License](LICENSE.md)

## ⚖️ Legal Warning

**Disclaimer**

The burpsuite extension `Auto-Payload-Positioner` and all related tools under this project are intended strictly for educational, research, and ethical security assessment purposes. They are provided "as-is" without any warranties, expressed or implied. Users are solely responsible for ensuring that their use of these tools complies with all applicable laws, regulations, and ethical guidelines.

By using `Auto-Payload-Positioner`, you agree to use them only in environments you are authorized to test, such as applications you own or have explicit permission to analyze. Any misuse of these tools for unauthorized reverse engineering, infringement of intellectual property rights, or malicious activity is strictly prohibited.

The developers of `Auto-Payload-Positioner` shall not be held liable for any damage, data loss, legal consequences, or other consequences resulting from the use or misuse of these tools. Users assume full responsibility for their actions and any impact caused by their usage.

Use responsibly. Respect intellectual property. Follow ethical hacking practices.

---

## 🙌 Contribute or Support

## Contributing

[![Contributions Welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat-square)](CONTRIBUTE.md)

- Found it useful? Give it a ⭐️
- Got ideas? Open an [issue](https://github.com/netsquare/Auto-Payload-Positioner/issues) or submit a PR
- Built something on top? DM me or mention me — I’ll add it to the README!

## Other Projects
- [The Browser Bruter](https://github.com/netsquare/BrowserBruter)

---

Built with ❤️ for all hackers!
