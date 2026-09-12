# Android_IDE API settings

The `data.settings` package provides a provider-neutral API configuration layer:

* manual input uses `ApiConfigDraft`; imported JSON uses the same validation path;
* ordinary values are stored through a Preferences DataStore boundary, while only an
  opaque alias is persisted for the key;
* API keys are AES-GCM encrypted using a non-exportable Android Keystore key and the
  encrypted vault directory is explicitly excluded from Android backup;
* `OpenAiCompatibleAdapter` owns OpenAI-compatible protocol details and exposes only a
  provider-neutral connection-test result to UI code.

## Import schema v1

Imports are strict JSON data (maximum 16 KiB). Unknown fields, coercions, unsupported
versions, invalid protocols, control characters, and overlong values are rejected.
No value from the file is evaluated or used to load code.

```json
{
  "schemaVersion": 1,
  "provider": "OPENAI_COMPATIBLE",
  "baseUrl": "https://api.example.com/v1/",
  "model": "example-model",
  "apiKey": "replace-me",
  "allowInsecureHttp": false
}
```

HTTP must be explicitly enabled and produces a prominent warning. Applications should
never put a draft, decrypted key, request authorization header, or raw network exception
in logs, saved UI state, crash metadata, analytics, or backups. Use `SecretRedactor` at
all diagnostic boundaries and keep key display fields non-saveable.