# Stock A52s access and recovery validation

Status on 15 October 2026: pending. No Android phone is attached to the implementation environment. Automated tests establish modeled constraints, not stock Samsung call access or audible recovery.

Test consumer companion association and ADB enrollment separately. Record model, exact firmware, Android/One UI, accessory, app version and enrollment. Do not export phone numbers, addresses or complete platform objects.

1. Install the signed experimental build. Preserve Samsung Phone as the calling UI. Grant Bluetooth access and complete the chosen enrollment once.
2. Verify the app reports configured access. Place or accept an ordinary non-emergency call while parked; verify Telecom actually binds the service. Record current access evidence and the last binding observation separately.
3. With the same intended Bluetooth endpoint selected, observe one automatic transaction. Check that departure and return occurred, every request appears in the report, and both physical audio directions work through the accessory.
4. Repeat after process recreation, a normal reboot and an application update. Revoke access and confirm the app stops mutations. A dirty previous transaction should suppress another bounce until a complete call end is observed.
5. Test handset/speaker/wired choices, a second call, hold, target loss and duplicate accessories. No repair should override these changes. Use simulated emergency metadata; never place a real emergency call for testing.
6. Reproduce the original fault before an intervention. Record whether automatic recovery restored both audio directions without a route tap, repair button, Bluetooth toggle, re-pair or reboot.
7. Compare prevention ON and OFF with otherwise unchanged settings over seven days. Keep total eligible calls, reproduced faults, automatic recoveries, failed recoveries and manual corrections separate.

Publish sanitized results in the compatibility data. A stable period without a reproduced fault remains an observation, not demonstrated recovery. If association or a public route request cannot repair a vendor fault, record the limitation and retain the experimental channel.
