# Detection source mapping

The implementation intentionally mirrors only source-backed location spoofing signals.

| Caught_You implementation | Supplied reference application source |
|---|---|
| `Settings.Secure["development_settings_enabled"]` | `J1/m.java` method `m()` |
| Developer Options participates in spoof decision | `BasActivity.isMockSettingsON()` |
| LocationManager mock-provider flag | `com/aebas/aebas_client/f0.java` `onLocationChanged()` |
| Fused mock-provider flag, API 31+ `isMock()` | `com/aebas/aebas_client/j0.java` |
| Fused mock-provider flag, pre-31 `isFromMockProvider()` | `com/aebas/aebas_client/j0.java` |
| Spoof decision blocks normal flow and invokes spoof warning | `BasActivity.captureFinger()` and `ShowMessageSpoofing()` |

The source also contains location quality/freshness gates (accuracy and elapsed-time checks). Those are not treated as spoof indicators in Caught_You; they are used by reference application to validate location acquisition. Native security routines are not included because the uploaded source archive did not include their native library implementation and their relationship to the spoofing branch was not established.
