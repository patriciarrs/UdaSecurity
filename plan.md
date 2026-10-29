# UdaSecurity Implementation Plan

## Goal

Complete the UdaSecurity project in the same order as the repository README. Use the rubric to make each step measurable: the multi-module Maven project must compile and run, all 11 Security Service behaviors must have isolated and meaningful unit tests, high-priority SpotBugs findings must be resolved, and the final application must be runnable from an executable JAR.

This is a plan only. No project files or application code have been changed.

## Implementation Steps

### 1. Find and add the missing Maven dependencies

1. Inspect the existing project structure, source imports, and current `pom.xml` to identify libraries that the application already uses.
2. Add the needed Maven artifacts and versions so Maven, rather than manually included JARs, resolves the dependencies.
3. Keep this initial dependency setup focused on getting the existing project to compile and launch; the dependency locations will be reorganized after the modules are created.
4. Run the application and confirm it starts without dependency or runtime errors.

**Completion check:** Maven resolves the dependencies, and the existing application launches.

### 2. Split the project into ImageService and SecurityService modules

1. Create a parent Maven project with a top-level `pom.xml` and two child modules: `ImageService` and `SecurityService`.
2. Move each existing source file and resource into the module that owns it:
   - Put image analysis classes, including the fake and AWS image implementations, in `ImageService`.
   - Put the security application, GUI, data, repository, and service classes in `SecurityService`.
3. Configure the parent POM to list both modules and hold only dependencies shared by both modules.
4. Put dependencies used by only one module in that module's child POM. Avoid duplicate dependency declarations across the parent and children.
5. Use the parent POM's `pluginManagement` to set the latest versions of the core Maven lifecycle plugins, such as `maven-compiler-plugin` and `maven-surefire-plugin`. Configure Surefire so tests can run under the Java module system, including the required `--add-opens` argument for Mockito to access the test package.
6. Add a `module-info.java` descriptor to each module:
   - Declare only the packages and modules each module needs.
   - Export the ImageService API needed by SecurityService, and require ImageService from SecurityService; do not make ImageService depend on SecurityService.
   - Add required third-party modules, including any transitive libraries that are not declared correctly by their dependencies.
   - Open only the SecurityService data package to Gson for reflective serialization.
7. Build from the parent directory and launch the application. Resolve module-path, missing `requires`, `exports`, or `opens` errors as they appear. Also confirm the application main class can be launched from IntelliJ.

**Completion check:** `mvn compile` works from the parent project; both modules are correctly separated; the application starts.

### 3. Refactor for testability and write Security Service unit tests

1. Review the Security Service's responsibilities and identify behavior that currently lives in the GUI or another class but belongs in the Security Service.
2. Move only the business logic needed to make the specified behaviors testable, keeping the application behavior intact.
3. Define an image-service interface that captures the behavior required by SecurityService. Make both `AwsImageService` and `FakeImageService` usable through this interface.
4. Create a finite state machine diagram to make the alarm behavior easier to reason about:
   - Identify the relevant system and alarm states, including disarmed, armed-home, armed-away, no alarm, pending alarm, and alarm.
   - Draw transitions caused by arming or disarming, sensor activation or deactivation, and camera scan results.
   - Compare the diagram with all 11 documented behaviors and use it to identify transition scenarios that need tests.
   - Save the completed diagram in `starter/catpoint-parent`.
5. Create `SecurityServiceTest` under the Maven test-source directory, using the same Java package as `SecurityService`.
6. Add the required JUnit 5 and Mockito test dependencies to the appropriate module POM, without duplicating them in multiple POMs.
7. Use Mockito `@Mock` dependencies for the repository and image-service dependencies, so each unit test exercises SecurityService alone and does not depend on those implementations. Use `ArgumentMatchers` when configuring or verifying mock interactions that need argument matching.
8. Use `@BeforeEach` for common test setup, descriptive test names that state the condition and expected result, and JUnit 5 parameterized tests where related scenarios can share a test.
   - Follow the course test-naming convention: do not include the word `test`; begin with the method or scenario under test; include the condition; end with the expected result; and separate name fragments with underscores.
   - Use `@DisplayName` to provide a readable description with spaces and special characters. Each display name should match the corresponding required-behavior description in the list below.
   - Do not use `@InjectMocks`, Mockito `lenient`, or the `mock()` method in tests; these were not covered in the course.
9. Add one or more focused tests for every required behavior:
   1. If alarm is armed *and* a sensor becomes activated, put the system into pending alarm status.
   2. If alarm is armed *and* a sensor becomes activated *and* the system is already pending alarm, set off the alarm.
   3. If pending alarm *and* all sensors are inactive, return to no alarm state.
   4. If alarm is active, change in sensor state should not affect the alarm state.
   5. If a sensor is activated *while* already active *and* the system is in pending state, change it to alarm state.
   6. If a sensor is deactivated *while* already inactive, make no changes to the alarm state.
   7. If the camera image contains a cat *while* the system is armed-home, put the system into alarm status.
   8. If the camera image does not contain a cat, change the status to no alarm *as long as* the sensors are not active.
   9. If the system is disarmed, set the status to no alarm.
   10. If the system is armed, reset all sensors to inactive.
   11. If the system is armed-home *while* the camera shows a cat, set the alarm status to alarm.
10. Use the tests to expose missing or unclear behavior; distinguish an incorrect test expectation from a real application defect before changing production logic.

**Completion check:** `mvn test` from the parent project runs `SecurityServiceTest` successfully using the module's JUnit 5 and Mockito dependencies; every listed behavior is covered without relying on real repository or image-service behavior.

**Optional — connect to AWS image recognition:** Follow the instructions in `AwsImageService`, create the required AWS credentials and ImageService resource configuration, inject `AwsImageService` in the GUI instead of `FakeImageService`, then try image scans. This requires AWS account access and must remain optional; the default application should still have a usable non-AWS setup.

### 4. Fix application bugs found by the unit tests

1. Review each failing test against the corresponding application requirement.
2. Correct the Security Service logic that is missing or producing the wrong state transition.
3. Preserve the expected behavior of unrelated flows and keep fixes in the appropriate service rather than adding logic to the tests or GUI.
4. Re-run the complete unit-test suite after each coherent fix and confirm all 11 behaviors pass.

**Completion check:** The unit tests pass and the implementation satisfies all 11 documented behaviors.

### 5. Check Security Service unit-test coverage

1. Run the unit tests with coverage in IntelliJ or an equivalent coverage tool.
2. Inspect coverage for each SecurityService method with more than one line of code that implements an application requirement, ensuring every line and branching condition is exercised. Trivial getters and setters do not need coverage.
3. Add tests for uncovered behavior branches; do not spend time testing trivial getters or setters solely to increase a percentage.
4. Confirm the tests still isolate SecurityService with mocks and that every meaningful line in methods implementing the requirements can be reached.

**Completion check:** Every SecurityService method with more than one line of code that implements an application requirement, including all its branches, is covered by unit tests; trivial getters and setters are excluded.

**Optional — add integration tests:** Implement a `FakeSecurityRepository` that behaves like the pretend database repository without reading or writing property files. Add `SecurityServiceIntegrationTest` to exercise service operations against that fake and assert the resulting values. Keep these tests separate from the required mocked unit tests.

**Optional — additional stand-out testing:** Add a focused spy-based unit test if it helps verify a meaningful interaction. If pursuing broader service integration, test the SecurityService/repository interaction using the fake repository rather than relying on property-file side effects.

### 6. Build and run an executable JAR

1. Configure the appropriate Maven packaging plugin so the runnable application module produces an executable JAR with the correct main class and runtime dependencies.
2. Build the JAR from the parent project using the documented Maven build goal.
3. Run it from the command line using `java -jar [yourjarname]`.
4. Verify the GUI launches and its operations can be used without exceptions.
5. Capture the required command-line/application screenshot as `executable_jar.png` for submission.

**Completion check:** The packaged application launches from the JAR, and the screenshot is saved for submission.

### 7. Add SpotBugs static analysis

1. Configure the SpotBugs Maven plugin under the POM `reporting` section.
2. Run the reporting/build command from the parent project (the rubric specifies `mvn install site`).
3. Locate and inspect `spotbugs.html` under the SecurityService module's `target/site` directory.
4. Fix every High-priority finding, including reviewing the rubric's note about moving `StyleService` into SecurityService if that class is relevant.
5. Re-run SpotBugs to confirm High-priority findings are gone; optionally address lower-priority findings when practical.
6. Include the generated report in the project submission.

**Completion check:** The report is generated in the expected module directory and contains no unresolved High-priority findings.

### 8. Final project and rubric verification

1. From the parent project directory, verify `mvn compile`, `mvn test`, and the documented packaging/reporting build work; confirm `mvn test` actually executes the tests.
2. Launch the executable JAR again and check the GUI's core operations.
3. Confirm the parent POM has only shared dependencies, the child POMs have module-specific dependencies, and no dependency is unnecessarily duplicated.
4. Confirm both module descriptors have the required exports, requires, and narrowly scoped Gson opening.
5. Confirm `SecurityServiceTest` uses JUnit 5, Mockito `@Mock`, and `ArgumentMatchers` as appropriate; verify all 11 requirements and the required method/branch coverage; confirm the SpotBugs report and JAR screenshot are present.
6. Submit the complete project folder through GitHub or as a ZIP, and cross-check each item against `rubric_requirements.md`.

## Notes and scope

- The steps above follow README Sections 1–7, with final submission checks after those sections.
- AWS integration, integration tests, and extra stand-out tests are explicitly optional; they do not block completion of the core project. The finite state machine diagram is a planned required step.
- The exact Maven artifact versions and Java module names should be selected after inspecting the current project and Java version, rather than assumed in advance.
- No implementation is included in this planning task.
