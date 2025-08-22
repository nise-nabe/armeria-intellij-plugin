# Armeria IntelliJ Plugin
Armeria IntelliJ Plugin provides IntelliJ IDEA support for the Armeria microservice framework by LINE Corporation. This plugin adds project templates, run configurations, framework detection, and code completion for Armeria applications.

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Prerequisites and Environment Setup
- **CRITICAL**: Requires Java 17 or higher (originally targets Java 21+ but can work with Java 17)
- Uses Gradle 8.14.3 with Gradle Wrapper
- **NETWORK DEPENDENCY**: IntelliJ Platform dependencies require internet access
- **VERSION CONSTRAINTS**: IntelliJ IDEA version must be compatible with plugin version

### Bootstrap and Build Process
1. **Initial Setup**:
   ```bash
   ./gradlew tasks
   ```
   - **EXPECTED TIME**: 1-2 minutes for task discovery
   - **NEVER CANCEL**: Wait for task listing to complete

2. **Build the Plugin**:
   ```bash
   ./gradlew buildPlugin --no-daemon --console=plain
   ```
   - **EXPECTED TIME**: 15-45 minutes (includes IntelliJ Platform download on first build)
   - **NEVER CANCEL**: Set timeout to 60+ minutes
   - **CRITICAL**: First build downloads ~500MB+ of IntelliJ Platform dependencies
   - **NETWORK REQUIREMENT**: Requires internet access to download from:
     - `download.jetbrains.com` 
     - `cache-redirector.jetbrains.com`
     - `www.jetbrains.com/intellij-repository`
   - **FAILURE MODE**: Build fails with "No address associated with hostname" if network access is restricted

3. **Running Tests**:
   ```bash
   ./gradlew test --no-daemon --console=plain
   ```
   - **EXPECTED TIME**: 10-15 minutes  
   - **NEVER CANCEL**: Set timeout to 30+ minutes
   - Tests use IntelliJ Platform test framework and can be slow
   - **NETWORK DEPENDENCY**: Also requires internet access for IntelliJ Platform dependencies

### Build Configuration Issues
- **COMMON PROBLEM**: Java toolchain version conflicts
  - Original config requires Java 23 (bleeding edge)
  - Modify `gradle/gradle-daemon-jvm.properties` toolchainVersion to match available Java
  - Update `build.gradle.kts` java toolchain configuration if needed
- **COMMON PROBLEM**: IntelliJ Platform version not found
  - Original uses `intellijIdeaUltimate("2025.1.4.1")` (EAP version)
  - Try stable versions like `intellijIdeaCommunity("2024.1")` for compatibility
- **NETWORK ISSUES**: Add `org.gradle.java.installations.auto-download=false` to `gradle.properties` to disable auto-provisioning

### Working in Restricted Environments
If you cannot build due to network restrictions:
1. **Document the limitation**: Note "build fails due to network restrictions" in any changes
2. **Focus on source code changes**: Make changes to `.kt` files, templates, and resources
3. **Validate syntax**: Use IDE or `kotlinc` for basic syntax checking
4. **Review templates**: Manually inspect file templates in `src/main/resources/fileTemplates/`
5. **Test file generation**: Examine template logic without running full build

### Running the Plugin
1. **Run IDE with Plugin**:
   ```bash
   ./gradlew runIde --no-daemon
   ```
   - **EXPECTED TIME**: 5-10 minutes to start
   - **NEVER CANCEL**: Set timeout to 15+ minutes
   - Launches IntelliJ IDEA with the plugin installed

2. **Prepare Plugin Distribution**:
   ```bash
   ./gradlew buildPlugin
   ```
   - Creates distributable ZIP in `build/distributions/`

## Validation

### Manual Testing Scenarios
After making changes, ALWAYS test these scenarios:
1. **Project Creation**: Test creating new Armeria project using File → New → Project → Armeria
2. **Template Generation**: Verify build.gradle.kts templates generate correctly for Kotlin/Java/Scala
3. **Run Configuration**: Test Armeria run configuration creation and execution
4. **Framework Detection**: Verify framework detection works in existing Armeria projects

### Build Validation
- **Always run** after code changes:
  ```bash
  ./gradlew build --no-daemon
  ```
- **Lint/Format checks**: No explicit linter configured, rely on Kotlin compiler warnings
- **Plugin verification**:
  ```bash
  ./gradlew verifyPlugin
  ```

### Testing Limitations
- **Cannot interact with IDE UI**: Running `runIde` launches GUI but automation cannot interact
- **Network-dependent tests**: Some tests may fail without internet access
- **Platform-specific**: Tests use IntelliJ Platform APIs that may behave differently across OS

## Common Tasks

### Repository Structure
```
.
├── .github/workflows/main.yml    # CI/CD configuration
├── build.gradle.kts             # Main build configuration
├── gradle.properties            # Gradle settings
├── settings.gradle.kts          # Project settings
├── src/main/kotlin/             # Plugin source code
│   ├── framework/               # Framework detection and support
│   ├── module/                  # Project template generation
│   ├── run/                     # Run configuration provider
│   └── starters/                # Starter templates
├── src/main/resources/
│   ├── META-INF/plugin.xml      # Plugin descriptor
│   ├── fileTemplates/j2ee/      # Gradle/Maven templates
│   └── messages/                # Internationalization
└── src/test/kotlin/             # Test code (27 Kotlin files total)
```

### Key Components
- **ArmeriaModuleBuilder**: Generates new Armeria projects with templates
- **ArmeriaRunConfiguration**: Custom run configuration for Armeria applications
- **ArmeriaFrameworkEx**: Framework detection and support
- **File Templates**: Gradle/Maven build file templates with Armeria dependencies

### Gradle Tasks Reference
- `./gradlew tasks` - List all available tasks (1-2 minutes)
- `./gradlew buildPlugin` - Build plugin distribution (15-30 minutes)
- `./gradlew runIde` - Run IntelliJ with plugin (5-10 minutes startup)
- `./gradlew test` - Run all tests (10-15 minutes)
- `./gradlew verifyPlugin` - Verify plugin structure and compatibility
- `./gradlew publishPlugin` - Publish to JetBrains marketplace (requires auth)

### Common File Locations
- Plugin descriptor: `src/main/resources/META-INF/plugin.xml`
- Build templates: `src/main/resources/fileTemplates/j2ee/`
- Message bundles: `src/main/resources/messages/ArmeriaBundle.properties`
- Main plugin classes: `src/main/kotlin/module/ArmeriaModuleBuilder.kt`
- Test files: `src/test/kotlin/FileTemplateTest.kt`

### Dependencies and Versions
- **Kotlin**: Uses latest stable (defined in settings.gradle.kts)
- **IntelliJ Platform**: Uses 2025.1.4.1 EAP (may need adjustment for builds)
- **Gradle**: 8.14.3 via wrapper
- **Test frameworks**: JUnit 5, MockK for testing

### CI/CD Notes
- Runs on Ubuntu, Windows, and macOS
- Originally configured for Java 23 (updated to Java 17 for compatibility)
- Build matrix tests across multiple platforms
- Uses JetBrains toolchain for consistent Java versions

## Known Issues
- **CRITICAL NETWORK DEPENDENCY**: Build requires unrestricted internet access for IntelliJ Platform downloads
  - Fails with "No address associated with hostname" in restricted environments
  - Must be able to reach: `download.jetbrains.com`, `cache-redirector.jetbrains.com`
- **Version sensitivity**: Plugin heavily dependent on specific IntelliJ and Java versions
- **Large downloads**: First build downloads substantial IntelliJ Platform dependencies (~500MB+)
- **EAP versions**: Original configuration uses bleeding-edge IntelliJ versions that may not be available
- **Toolchain conflicts**: May require adjusting Java version in `gradle/gradle-daemon-jvm.properties`

**IMPORTANT**: If builds fail due to network restrictions, document the failure in any changes made. Do not skip validation - note that "build fails due to network limitations" in your change documentation.