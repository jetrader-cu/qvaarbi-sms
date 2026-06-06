# Incoming SMS to URL forwarder

This is a free, open-source Android app that automatically forwards incoming SMS messages to a specified URL as JSON via HTTP POST.
* Forward SMS from specific numbers or all senders.
* Retries failed requests with exponential backoff.
* Optionally stores messages that exhaust all retries so you can re-send them later.
* Includes sender, message, timestamp, SIM slot, and more.
* Forward messages directly to Telegram bots or channels.
* Optional heartbeat ping so an external monitor can alert you if the phone goes offline.
* Built-in test message sender and error log viewer.
* No cloud services or user registration required.

## Help Improve This Project

If you've used or tested it, please take a minute to fill out [this short survey](https://forms.gle/c5YY7C81X33VjxyZ8) – it helps me understand real-world usage and prioritize new features. Thank you!

## Download apk

Download apk from [release page](https://github.com/bogkonstantin/android_income_sms_gateway_webhook/releases)

Or download it from F-Droid

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/tech.bogomolov.incomingsmsgateway/)

## How to use

Set up App Permissions for you phone after installation. For example, enable "Autostart" if needed
and "Display pop-up windows while running in the background" from Xiaomi devices.

Set sender phone number or name and URL. It should match the number or name you see in the SMS messenger app. 
If you want to send any SMS to URL, use * (asterisk symbol) as a name.  

Every incoming SMS will be sent immediately to the provided URL.
If the response code is not 2XX or the request ended with a connection error, the app will try to
send again up to 10 times (can be changed in parameters).
Minimum first retry will be after 10 seconds, later wait time will increase exponentially.
If the phone is not connected to the internet, the app will wait for the connection before the next
attempt.  

If at least one Forwarding config is created and all needed permissions granted - you should see F
icon in the status bar, means the app is listening for the SMS.

Press the Test button to make a test request to the server.

Press the Syslog button to view errors stored in the Logcat.

### Optional Features

#### Sign with HMAC-SHA-256
Selecting this option will allow you to sign the request with a provided secret. The hex signature 
is created from the request payload and the provided secret, and will be added to the request with 
the header `X-Signature`.

#### Store failed messages for retry
This is a per-rule option in the forwarding config. When enabled, any message that exhausts all of
its automatic retries (or fails permanently) is kept on the device instead of being dropped. The
action bar then shows a **Retry N failed** item — tap it to re-send every stored message. Messages
that fail again stay in the store so you can try once more later.

#### Heartbeat monitoring
Open **Settings** from the action bar to enable a periodic heartbeat. While enabled, the app POSTs to
a URL you provide at a chosen interval (in minutes), so an external dead-man's-switch monitor (e.g.
[healthchecks.io](https://healthchecks.io), Uptime Kuma push monitors, cronitor) can alert you if the
phone dies, is killed, or loses connectivity and the pings stop. Lower intervals detect failures
sooner but use more battery and data. Use the **Test** button to send one ping immediately.

### Request info
HTTP method: POST  
Content-type: application/json; charset=utf-8  

Sample payload:  
```json
{
     "from": "%from%",
     "text": "%text%",
     "sentStamp": "%sentStamp%",
     "receivedStamp": "%receivedStamp%",
     "sim": "%sim%"
}
```

Available placeholders:
%from%
%text%
%sentStamp%
%receivedStamp%
%sim%
%version% (app version name)
%battery% (battery charge 0-100, or -1 if unknown)
%power% (power source: ac / usb / wireless / unplugged / unknown)
%network% (active network: wifi / mobile / ethernet / other / none)
%Regex=...%

The device-health placeholders `%version%`, `%battery%`, `%power%` and `%network%`
report the phone's state at the moment the SMS is forwarded — useful for monitoring
a fleet of devices (see issue #39). `%battery%` is a number, so use it unquoted
(`"battery": %battery%`); the others are strings and go inside quotes. None of them
requires an extra runtime permission.

#### Extracting part of a message with `%Regex=...%`

Use `%Regex=<pattern>%` to forward only a part of the SMS body instead of the whole
`%text%` — handy for pulling out an OTP code, a transaction amount, etc.

The pattern is a standard Java regular expression applied to the message text:

* If the pattern contains a capturing group `( ... )`, the content of the **first
  group** is inserted.
* If it has no group, the **whole match** is inserted.
* If the pattern matches nothing, an **empty string** is inserted.
* An invalid pattern is ignored (empty string) and never crashes forwarding.

Matching is case-sensitive; prefix the pattern with `(?i)` to make it
case-insensitive. The extracted value is JSON-escaped, so it is safe to place
inside a JSON string. The first *unescaped* `%` ends the pattern, so to match a
literal percent sign in the message, escape it as `\%`.

Example — extract a numeric OTP code from a message like `Your code is 123456`:
```json
{
     "from": "%from%",
     "text": "%text%",
     "code": "%Regex=code is (\\d+)%"
}
```
sends `"code": "123456"`.

Example with an escaped percent — extract the number before `%` from
`Sale: 50% off`, using `%Regex=(\d+)\%%`:
```json
{
     "discount": "%Regex=(\\d+)\\%%"
}
```
sends `"discount": "50"`.

### Request example
Use this curl sample request to prepare your backend code
```bash
curl -X 'POST' 'https://yourwebsite.com/path' \
     -H 'content-type: application/json; charset=utf-8' \
     -d $'{"from":"1234567890","text":"Test"}'
```

### Send SMS to the Telegram

1. Create Telegram bot and channel to receive messages. [There](https://bogomolov.tech/Telegram-notification-on-SSH-login/) is short tutorial how to do that.  
2. Add new forwarding configuration in the app using this parameters:
   1. Any sender you need, * - on the screenshot
   2. Webhook URL - `https://api.telegram.org/bot<YourBOTToken>/sendMessage?chat_id=<channel_id>` - change URL using your token and channel id
   3. Use this payload as a sample `{"text":"sms from %from% with text: \"%text%\" sent at %sentStamp%"}`
   4. Save configuration

<img alt="Incoming SMS Webhook Gateway screenshot Telegram example" src="https://raw.githubusercontent.com/bogkonstantin/android_income_sms_gateway_webhook/master/fastlane/metadata/android/en-US/images/phoneScreenshots/telegram.png" width="30%"/> 

### Process Payload in PHP scripts

Since $_POST is an array from the url-econded payload, you need to get the raw payload. To do so use file_get_contents:
```php
$payload = file_get_contents('php://input');
$decoded = json_decode($payload, true);
```

### Screenshots
<img alt="Incoming SMS Webhook Gateway screenshot 1" src="https://raw.githubusercontent.com/bogkonstantin/android_income_sms_gateway_webhook/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="30%"/> <img alt="Incoming SMS Webhook Gateway screenshot 2" src="https://raw.githubusercontent.com/bogkonstantin/android_income_sms_gateway_webhook/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="30%"/> <img alt="Incoming SMS Webhook Gateway screenshot 3" src="https://raw.githubusercontent.com/bogkonstantin/android_income_sms_gateway_webhook/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="30%"/>

### Misc

This repository contains a stable app with minimum functionality. It is not archived, but not actively developing. If you need an app with merged PRs - try [this fork](https://github.com/scottmconway/android_income_sms_gateway_webhook)

### AI usage

Some of the code in this repository is written with the help of AI coding assistants.
I use AI as a tool for writing code — implementation, tests, and boilerplate — not for
architectural or design decisions, which I make myself. Every AI-assisted change is
reviewed and tested by a human before it is merged, and I remain responsible for all
code in this repository.
