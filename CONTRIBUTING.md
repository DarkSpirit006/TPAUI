# Contributing

Thanks for helping improve TPAUI.

## Bug reports

Before opening an issue, search existing reports. Include:

- TPAUI version
- Minecraft version and server software/build
- Java version
- Whether EssentialsX and Geyser/Floodgate are installed
- Steps to reproduce
- Relevant sanitized console output and configuration

Do not post passwords, tokens, IP addresses, or private server data.

## Development

TPAUI uses the Gradle 9.8.0 Wrapper, which requires JDK 17 or newer to build; the plugin output targets Java 8 bytecode. Use the checked-in Wrapper before opening a pull request:

**Windows**

```powershell
.\gradlew.bat clean build
```

**Linux/macOS**

```bash
./gradlew clean build
```

Keep changes focused. If a change affects a command, permission, setting, integration, or supported server version, update the README and `docs/` as appropriate.

## Pull requests

Please include a short explanation of the change and why it is needed. Check that:

- [ ] The project builds successfully.
- [ ] The plugin descriptor and default configuration are valid YAML.
- [ ] Commands and permissions match the documentation.
- [ ] Bedrock users do not receive Java chat-click actions or an addon cancel button/form.
- [ ] User-facing behavior and compatibility notes are documented.

## License

By contributing, you agree that your contribution is provided under the MIT License.
