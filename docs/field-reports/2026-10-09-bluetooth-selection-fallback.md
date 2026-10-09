# Bluetooth selection immediately falls back to earpiece

Reported with app 0.18.1 (21), Galaxy A52s SM-A528B, Android 14. The user
could be heard by the other party, but could not enable Bluetooth call output.
The report was exported while a call was still active.

## Observed evidence

The first session starts at 14:27:42. During ringing, the reported route changes
from Bluetooth to earpiece at 14:27:43.735. The engine immediately marks this
as `user left Bluetooth` and keeps that decision for the entire session.

During active audio, Bluetooth is selected at 14:28:25.150 and falls back to
earpiece 198 ms later. After Bluetooth is switched off and on, another selection
at 14:28:43.459 falls back after 253 ms. A second session starts at 14:29:58;
after answering, Bluetooth falls back after 177 ms. A further selection at
14:30:09.135 falls back after 483 ms. Whenever observed on Bluetooth, SCO is
false and the voice downlink is not reported on Bluetooth.

These transitions are consistent with the user's repeated attempts to activate
Bluetooth audio. The app observes Telecom routes, not screen taps; reconnects
and automatic routing can also produce those transitions. The log does not
establish which microphone carried the uplink or the underlying vendor failure.

The completed-call history only contains ended sessions. `CALL START` and
`phase=ACTIVE calls=1` establish that the second call was recognized even though
it was absent from that history at export time.

## Change and limits

- A ringing fallback or failed Bluetooth audio connection no longer latches the
  hands-off decision. Fault confirmation still precedes repair.
- Selecting Bluetooth again resumes diagnosis without resetting repair budgets.
- Rapid failed selections share the same connection-fault confirmation window.
  A Bluetooth route label with SCO still disconnected does not complete verification.
- Switching Bluetooth off or suspending call audio cancels an in-progress route
  operation; reconnection is diagnosed afresh. Disappearance of Bluetooth after a
  confirmed fault is not counted as successful recovery.
- Each rebuild retries its Bluetooth return request at most once.
- Leaving established Bluetooth audio for the phone remains respected, including
  the existing one-time grace after a repair. Speaker, wired and streaming
  choices cancel route operations.
- Audio callbacks are evaluated directly, preserving short Bluetooth selections.
  Diagnosis-note changes and a 30-second heartbeat make progress visible.
- Diagnostic exports include the active call with a provisional result.

This corrects a demonstrated detection failure. Telecom route requests may still
fail on the actual Samsung Bluetooth stack; JVM tests cannot prove hardware recovery.

## Real-device validation

1. Reproduce an incoming call with the paired car/headset and export during ringing
   and after answering. Confirm the active-call section reflects the session.
2. During the failure, select Bluetooth repeatedly. Confirm each brief route change
   appears and a persistent earpiece fallback leads to a bounded repair attempt.
3. Switch Bluetooth off and on during the call. Check HFP/SCO and route recovery.
4. Verify audio in both directions. `REPAIRED` is a signal-based assessment, not
   confirmation that the remote participant is audible.
5. Verify that choosing phone after a stable Bluetooth connection, speaker or a
   wired headset is respected; selecting Bluetooth again should resume diagnosis.
6. Export after hanging up and compare the final call report and attempt count.

No raw personal diagnostic attachment is committed.
