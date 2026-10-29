@ -1,237 +0,0 @@

# Catpoint user manual

Catpoint is a desktop simulation of a home-security system. You can create
virtual door, window, and motion sensors, choose when the system monitors them,
and scan pictures for cats. Its purpose is to demonstrate how sensor events and
camera results affect a security system's status.

**This is a demonstration, not a real security system.** The controls do not
connect to physical sensors, a live camera, emergency services, or a monitoring
provider. An alarm is an on-screen status, not an audible siren or an emergency
notification. The default image analyzer returns random results rather than
recognizing the contents of your picture.

The behavior described below is the intended operating model. Some transitions
are not yet implemented correctly; see [Current limitations](#current-limitations)
before trying the examples.

## Start the application

### Requirements

- A graphical desktop session; the Swing interface cannot run in a headless
  terminal or server session without a display.
- A JDK compatible with the project's Java 14 compilation target. JDK 21 is a
  suitable choice. Use the same JDK for Maven and for launching the application.
- Apache Maven 3.9 or later for the command-line instructions.
- Internet access on the first build to download Maven plugins and dependencies.

No AWS account or credentials are required for the default demo.

### From the command line

Open a terminal in this directory (`starter/catpoint-parent`), where the parent
`pom.xml` is located. Check that `java -version` and `mvn -version` report the
expected JDK, then run:

```sh
mvn install
mvn -pl SecurityService dependency:copy-dependencies -DincludeScope=runtime
```

The first command builds both modules and installs them in your local Maven
repository. The second prepares the runtime libraries in
`SecurityService/target/dependency`.

On macOS or Linux, launch with:

```sh
java -cp "SecurityService/target/classes:SecurityService/target/dependency/*" com.udacity.catpoint.application.CatpointApp
```

On Windows (PowerShell or Command Prompt), use a semicolon between classpath
entries:

```powershell
java -cp "SecurityService/target/classes;SecurityService/target/dependency/*" com.udacity.catpoint.application.CatpointApp
```

Keep the quotes and the `*` in the command. The window title is **Very Secure
App**, and the heading inside is **Very Secure Home Security**.

Repeat the build and dependency-preparation commands after source or dependency
changes. Otherwise, you can reuse the launch command. The current build does
not produce a self-contained executable JAR; `java -jar` is not a supported
startup method.

### From IntelliJ IDEA

1. Open this directory's `pom.xml` as a Maven project so both `ImageService`
   and `SecurityService` are imported.
2. Select a compatible JDK, such as JDK 21, as the project SDK and Maven runner
   JDK. Let IntelliJ finish loading the Maven dependencies.
3. Open
   `SecurityService/src/main/java/com/udacity/catpoint/application/CatpointApp.java`.
4. Run `CatpointApp.main()`, using the `SecurityService` module if prompted.

## Understand the system

Three different concepts work together:

- **Arming mode** determines whether monitoring is enabled.
- **Sensor activity** describes a simulated event, such as an open door or
  detected movement. An active sensor is not the same thing as an armed system.
- **Alarm status** describes the system's response to events.

For example, a sensor can be active while the system is disarmed without raising
an alarm. Conversely, an alarm can remain active after sensors become inactive.

### Arming modes

Choose a mode in **System Control**. The selected button is highlighted.

| Button              | Meaning                                                                                                                                                                             |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Disarmed**        | Monitoring is off: activating sensors does not raise an alarm. Selecting this mode clears the alarm to `NO_ALARM`. Use it when you want to stop monitoring or acknowledge an alarm. |
| **Armed - At Home** | Sensor monitoring is on, and a positive cat scan also triggers an alarm. Use this to demonstrate monitoring while someone is at home.                                               |
| **Armed - Away**    | Sensor monitoring is on. The cat-triggered alarm rule applies only to the at-home mode.                                                                                             |

Both armed modes treat all sensor types alike; the at-home mode does not bypass
motion sensors. Arming is intended to reset all sensors to inactive. There is no
entry/exit delay or PIN prompt.

### Alarm levels

The colored **System Status** label shows the alarm level, not the arming mode.

| Label in the window          | Internal status | Meaning                                                                                                                                     |
|------------------------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| **Cool and Good** (green)    | `NO_ALARM`      | No alarm is currently raised. This does not tell you whether monitoring is armed.                                                           |
| **I'm in Danger...** (amber) | `PENDING_ALARM` | An initial sensor event has raised a warning. A further activation can escalate it to a full alarm. This is a state, not a countdown timer. |
| **Awooga!** (red)            | `ALARM`         | A full alarm has been raised by further sensor activity or a cat detected in at-home mode.                                                  |

## Use the controls

### Add and operate sensors

In **Sensor Management**, enter a descriptive **Name**, select a **Sensor Type**
(`DOOR`, `WINDOW`, or `MOTION`), and click **Add New Sensor**.

Each sensor row shows its name, type, and **Active** or **Inactive** state.
Click **Activate** to simulate an event, or **Deactivate** to end it.
Click **Remove Sensor** to delete that sensor. To clear an alarm deliberately,
use **Disarmed** rather than removing sensors.

New sensors start inactive. Sensor types are labels only: all three follow the
same alarm rules. The interface allows up to four sensors. Although its limit
message mentions a Premium Membership, there is no subscription control in the
application; remove a sensor to make room for another.

### Load and scan a picture

1. Click **Refresh Camera** and select a readable image file, such as a JPEG or
   PNG. This loads a still-image preview; it does not access a webcam.
2. Click **Scan Picture** to analyze the selected image. Loading a new picture
   alone does not run analysis.
3. Read the camera heading: **DANGER - CAT DETECTED** or **Camera Feed - No Cats
   Detected**. Check **System Status** separately to see the alarm response.

Try the pictures included in this directory:
[sample-cat.jpg](sample-cat.jpg), [sample-not-cat.jpg](sample-not-cat.jpg), and
[sample-not-a-cat-fail.jpg](sample-not-a-cat-fail.jpg).

**The default analyzer guesses randomly on every scan.** A cat picture may
produce a no-cat result, a non-cat picture may produce a cat result, and repeated
scans of the same image may disagree. Filenames do not determine the result.
The heading reports the last scan result, not a continuous observation of the
preview. Always load a valid picture before scanning.

## Intended alarm rules

The following rules define the intended behavior. They are also illustrated in
the [security state-machine diagram](security-state-machine.md).

| Event or condition                                                            | Intended response                                                                                                |
|-------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| A sensor activates while armed, with no alarm currently raised.               | Move to `PENDING_ALARM`.                                                                                         |
| A sensor activates while armed and already pending.                           | Escalate to `ALARM`.                                                                                             |
| An active sensor is deactivated while pending, leaving every sensor inactive. | Return to `NO_ALARM`. If another sensor remains active, stay pending.                                            |
| Any sensor changes state while the full alarm is active.                      | Keep `ALARM`; ending a sensor event does not acknowledge the alarm.                                              |
| An already-active sensor receives another activation while pending.           | Escalate to `ALARM`.                                                                                             |
| An already-inactive sensor receives another deactivation.                     | Leave the alarm status unchanged.                                                                                |
| A scan detects a cat while armed at home.                                     | Set `ALARM`, without a pending stage.                                                                            |
| A scan reports no cat and all sensors are inactive.                           | Set `NO_ALARM`, even if an alarm was previously raised. If a sensor is active, leave the alarm status unchanged. |
| The system is disarmed.                                                       | Set `NO_ALARM`.                                                                                                  |
| Either armed mode is selected.                                                | Reset every sensor to inactive.                                                                                  |
| At-home mode is selected while the latest camera result indicates a cat.      | Set `ALARM` immediately; another scan should not be necessary.                                                   |

The repeated-activation and repeated-deactivation rules describe events handled
by the service. The sensor buttons only toggle state, so the interface does not
provide a way to send those repeated events directly.

### Walkthrough: a sensor alarm

1. Select **Disarmed** to begin with no alarm.
2. Add two sensors, for example `Front door` (`DOOR`) and `Hallway` (`MOTION`).
   If reusing saved sensors, deactivate them first.
3. Select **Armed - Away**.
4. Activate `Front door`. The status becomes **I'm in Danger...**.
5. Deactivate it while the other sensor is inactive. The status returns to **Cool and Good**.
6. Activate `Front door` again, then activate `Hallway`. The second activation
   escalates the status to **Awooga!**.
7. Select **Disarmed** to clear the alarm. Disarming does not itself deactivate
   the sensor rows.

### Walkthrough: a camera alarm

Select **Armed - At Home**, load a picture, and click **Scan Picture**. A positive
result changes the heading to **DANGER - CAT DETECTED** and raises **Awooga!**.
Because the analyzer is random, you may need more than one scan to see this.
Disarm to clear the alarm. Under the intended rules, a subsequent no-cat scan
also clears it if all sensors are inactive.

## Saved state and closing the application

Close the window to exit. Sensors, their activity, the arming mode, and the alarm
status are saved in Java's local user preferences, not in this project folder.
They are loaded again for the same operating-system user on the next launch.
Rebuilding the application does not reset them.

With no saved state, the application starts disarmed, with no alarm and no
sensors. The selected image and scan heading are not saved. After a restart,
select **Disarmed**, inspect the sensor rows, and load and scan an image before
starting a new demonstration.

## Current limitations

The current implementation differs from the intended alarm rules in these
ways:

- Arming does not yet reset sensors or reevaluate a previous positive cat
  result. Deactivate sensors manually before arming, and scan again after
  selecting at-home mode.
- Deactivating a sensor while pending clears the warning even if another sensor
  remains active. Deactivating a sensor during a full alarm lowers it to pending
  instead of preserving the alarm.
- Repeated activation of an already-active sensor is ignored rather than
  escalating a pending alarm.
- A no-cat scan clears the alarm even when a sensor is active. A positive scan
  outside at-home mode also clears the alarm. Avoid scanning during a
  sensor-only walkthrough if you want to observe sensor transitions alone.

These are behavior gaps, not evidence that a sensor has become safe or an
incident has been resolved. Use **Disarmed** for an explicit reset of the alarm.

## Troubleshooting

| Problem                                                               | What to check                                                                                                                                         |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `mvn` or `java` is not found.                                         | Install Maven and a compatible JDK, put them on `PATH`, and reopen the terminal. Check `mvn -version` for the JDK Maven actually uses.                |
| The build cannot resolve dependencies.                                | Check network/proxy access and run `mvn install` from this directory before preparing `SecurityService` dependencies. Both modules must be available. |
| The launch reports a missing main class or library.                   | Run both preparation commands again, launch from `starter/catpoint-parent`, and use the correct classpath separator for your OS.                      |
| The launch reports `HeadlessException` or a display connection error. | Run in a local graphical desktop session with a working display, not a headless environment.                                                          |
| **Invalid image selected.** appears.                                  | Choose a readable image file, not a directory or a renamed non-image file, and confirm the preview loads before scanning.                             |
| The cat result appears wrong or changes between scans.                | This is expected with the random demo analyzer; it does not recognize image contents.                                                                 |
| A sensor cannot be added.                                             | The interface caps the list at four sensors; remove one before adding another.                                                                        |
| Old sensors or an alarm reappear after restarting.                    | Local preferences preserve them. Disarm, then deactivate or remove the saved sensors through the interface.                                           |