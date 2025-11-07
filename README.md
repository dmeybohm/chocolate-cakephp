# Chocolate CakePHP

<!-- Plugin description -->
An IntelliJ IDEA/PhpStorm plugin that provides code completion, navigation, and IDE integration for CakePHP framework projects.

The plugin understands CakePHP's conventions and provides navigation between MVC components, autocompletion for framework-specific patterns, and type resolution for view variables.
<!-- Plugin description end -->

## Features

### Core Functionality

- **MVC Navigation**: Navigate between controllers, views, models, components, and helpers using keyboard shortcuts or gutter icons
- **Code Completion**: Context-aware completion for CakePHP-specific methods, properties, and conventions
- **View Variable Type Resolution**: Resolves and provides type hints for variables passed from controllers to views
- **Framework Version Support**: Supports CakePHP 5, 4, 3, and 2 (CakePHP 2 disabled by default)

### Supported Features

#### Controller & View Integration
- Create view files from controller actions using the gutter icon menu
- Support for multiple view formats (PHP, JSON, XML) with configurable data view types
- Navigate from controller actions to their corresponding views
- Navigate back from views to controllers using `Ctrl+Alt+Shift+Home` (Windows/Linux) or `Cmd+Option+Shift+Up` (Mac)
- Autocompletion for variables set in controllers when working in view files

#### Model & Database Layer
- Navigate to and autocomplete custom finder methods in models

#### Components & Helpers
- Component suggestions in controllers
- Complete and navigate to view helpers in views and other helpers
- Support for helper-to-helper references and completion

#### Additional Navigation
- Navigate to element files from `$this->element()` calls
- Jump to template files from render calls
- Navigate to CSS/JS files from asset method calls
- Support for CakePHP plugins with configurable paths
- Navigate between themed views and base views

### Configuration

The plugin detects CakePHP projects and enables features based on the framework version detected. Configuration options include:

- App namespace configuration and CakePHP version selection
- Custom plugin paths for your project
- Theme directory configuration
- Custom data view formats (RSS, CSV, etc.) in addition to JSON/XML

## Installation

You can install it from the plugin page on the [JetBrains
Marketplace](https://plugins.jetbrains.com/plugin/10006-chocolate-cakephp)

### From PhpStorm / IntelliJ Ultimate

1. Open PhpStorm/IntelliJ IDEA
2. Go to **Settings/Preferences** → **Plugins**
3. Click on **Marketplace** tab
4. Search for "Chocolate CakePHP"
5. Click **Install**
6. Restart your IDE

### Manual Installation

1. Download the latest release from the [releases page](https://github.com/dmeybohm/chocolate-cakephp/releases)
2. Go to **Settings/Preferences** → **Plugins**
3. Click the gear icon → **Install Plugin from Disk...**
4. Select the downloaded plugin file
5. Restart your IDE

## Getting Started

1. Open a CakePHP project in PhpStorm/IntelliJ IDEA
2. The plugin detects the CakePHP version and enables appropriate features
3. Verify configuration in **Settings** → **PHP** → **Frameworks** → **CakePHP**
4. Use `Ctrl+Click` (Cmd+Click on Mac) to navigate between MVC components
5. Look for cake icons in the gutter for available actions

## Keyboard Shortcuts

| Action                      | Windows/Linux                       | macOS                                  |
|-----------------------------|-------------------------------------|----------------------------------------|
| Navigate Controller → View  | Click gutter cake icon              | Click gutter icon                      |
|                             | `Ctrl+Alt+Shift+Home`               | `Cmd+Option+Shift+Up`                  |
| Navigate View → Controller  | Click floating cake icon in toolbar | Click floating cake icon in toolbar    |
|                             | `Ctrl+Alt+Shift+Home`               | `Cmd+Option+Shift+Up`                  |
| Create View from Controller | Click gutter icon                   | Click gutter icon                      |
|                             | Ctrl+click gutter when view exists  | Cmd+Click gutter icon when view exists |
| Navigate to element         | Ctrl+B on element name              | Cmd+B on element name                  |
|                             | Ctrl+click on element name          | Cmd+Click on element name              |

## Screenshots

### Controller Navigation
![CakePHP Controller Features](https://github.com/dmeybohm/chocolate-cakephp/blob/main/screenshots/cake3-controller.gif?raw=true)

### Configuration Screen
![Main config screen](https://github.com/dmeybohm/chocolate-cakephp/blob/main/screenshots/main-preferences.gif?raw=true)

### Plugin Configuration
![Plugin configuration](https://github.com/dmeybohm/chocolate-cakephp/blob/main/screenshots/plugin-preferences.gif?raw=true)

## Version Compatibility

| Plugin Version | CakePHP Versions | PhpStorm Version | IntelliJ IDEA |
|---------------|------------------|------------------|---------------|
| 1.0.0 | 5.x, 4.x, 3.x, 2.x* | 2023.3+ | 2023.3+ (Ultimate) |
| 0.9.x | 5.x, 4.x, 3.x, 2.x* | 2022.1+ | 2022.1+ (Ultimate) |

*CakePHP 2 support is disabled by default but can be enabled in settings

## Contributing

Contributions are welcome. For major changes, please open an issue first to discuss the proposed changes.

### Prerequisites

#### Required IDE

You can develop this plugin using either:
- **IntelliJ IDEA Community Edition** (free)
- **IntelliJ IDEA Ultimate Edition**

Note: While the plugin provides features for PhpStorm users, you only need IntelliJ IDEA (Community or Ultimate) to build and develop the plugin itself.

#### Required IntelliJ Plugin

The **Swing UI Designer** plugin is required to build this project. Without it, the `.form` GUI files will not compile correctly and the plugin's settings dialogs will hang.

**Installation:**
1. In IntelliJ IDEA, go to **Settings/Preferences** → **Plugins**
2. Click on **Marketplace** tab
3. Search for "Swing UI Designer"
4. Install the plugin from: https://plugins.jetbrains.com/plugin/25304-swing-ui-designer
5. Restart IntelliJ IDEA

This plugin enables proper compilation of the configuration UI forms located in `src/main/java/com/daveme/chocolateCakePHP/*.form`.

### Development Setup

#### Requirements

- **JDK 17 or higher** - Required to build the plugin
  - OpenJDK, Oracle JDK, Azul Zulu, Eclipse Temurin, Amazon Corretto, or other JDK distributions are supported
  - Set `JAVA_HOME` environment variable to your JDK installation path
  - The Gradle wrapper handles Gradle itself - you only need to provide the JDK
- **Swing UI Designer plugin** - See Prerequisites section above

#### Build Steps

1. Clone the repository
2. Open the project in IntelliJ IDEA (Community or Ultimate)
3. Run `./gradlew buildPlugin` to build the plugin
4. Run `./gradlew runIde` to test in a sandboxed IDE instance

### Running Tests

Ensure JDK 17+ is installed and `JAVA_HOME` is set:

```bash
./gradlew test
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history and changes.

## Issues & Support

Report bugs or request features in the [issue tracker](https://github.com/dmeybohm/chocolate-cakephp/issues).

## Resources

- [Plugin Homepage](https://plugins.jetbrains.com/plugin/10006-chocolate-cakephp)
- [GitHub Repository](https://github.com/dmeybohm/chocolate-cakephp)
- [CakePHP Documentation](https://book.cakephp.org/)
