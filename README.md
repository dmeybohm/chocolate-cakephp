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
- Navigate to table/model definitions from `fetchTable()` or `loadModel()` calls
- Navigate to and autocomplete custom finder methods in models
- Type hints and navigation for associated models/tables
- Completion for query builder methods

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

### From JetBrains Marketplace

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

One keyboard shortcut exists 

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

### Development Setup

1. Clone the repository
2. Open the project in IntelliJ IDEA
3. Run `./gradlew buildPlugin` to build the plugin
4. Run `./gradlew runIde` to test in a sandboxed IDE instance

### Running Tests

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew test
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history and changes.

## Issues & Support

Report bugs or request features in the [issue tracker](https://github.com/dmeybohm/chocolate-cakephp/issues).

## License

Licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Resources

- [Plugin Homepage](https://plugins.jetbrains.com/plugin/18065-chocolate-cakephp)
- [GitHub Repository](https://github.com/dmeybohm/chocolate-cakephp)
- [CakePHP Documentation](https://book.cakephp.org/)
- [PhpStorm Documentation](https://www.jetbrains.com/phpstorm/documentation/)
