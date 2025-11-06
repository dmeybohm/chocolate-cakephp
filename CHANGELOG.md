<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Chocolate CakePHP Changelog

## [Unreleased]

## [1.0.0] - 2025-10-31

### Added
- Add ability to navigate to table from `fetchTable()` calls
- Add autocompletion for tables for `fetchTable()` calls
- Add ability to navigate from `TableLocator::get()` to table classes
- Add autocompletion for `TableRegistry::get()` and `TableRegistry::getTableLocator()->get()`
- Add autocompletion and type hints for view variables in templates
- Add support for navigating to and from data views (JSON/XML)
- Add support for navigating / autocompletion from asset helper methods to asset files
- Add auto-detection of CakePHP version and configuration from composer.json
- Add auto-detection of app namespace from AppController
- Add support for configuring theme paths in plugin settings
- Add support for controllers nested not at the top-level for various features
- Add support for implicit render in controllers
- Add support for `ViewBuilder()->setTemplate()` and `ViewBuilder()->setTemplatePath()` in CakePHP 3+ controllers
- Add support for `$this->view` field assignment in CakePHP 2 controllers

### Changed
- Change default keyboard shortcut for toggling between controller and view from `Ctrl-Alt-Up` to `Ctrl-Alt-Shift-Home` (Windows/Linux) and `Ctrl-Meta-Shift-Up` (Mac) to avoid conflicts
- Rename toggle action from "Toggle between controller and view" to more descriptive name
- Improve handling of nested elements in templates
- Improve flexibility when handling plugin and theme template paths

### Fixed
- Fix view variable type hints in JSON/XML data views
- Fix Cake icon appearing in inappropriate file contexts
- Fix auto-detection settings not saving properly for new projects
- Fix plugin running during IDE indexing (dumb mode)

## [0.9.2] - 2024-11-07

### Fixed
- Fix exception thrown during indexing

## [0.9.1] - 2024-06-19

### Fixed
- Fix navigation between controller and view when path contains a slash

## [0.9.0] - 2024-05-17

### Added
- Add ability to create new view files from the controller
- Show pop-up menu in controller when multiple view files could be associated
- Add support for data views (JSON or XML by default)
- Add floating Cake icon in views for navigating back to the controller
- Add shortcut `Ctrl-Alt-Up` / `Cmd-Option-Up` for navigating from view back to controller
- Add configuration for adding additional data views types (e.g RSS, CSV etc)
- Add support for navigating to custom finders in `find` methods on tables
- Added support for autocompleting tables in CakePHP 3-5
- Add autocompletion for other ViewHelpers in ViewHelpers

### Changed
- Diabled CakePHP 2 support by default

### Removed
- Remove autocomplete for app namespace in settings

### Fixed
- Remove nested completion on components in controllers

## [0.8.3] - 2023-11-11

### Changed
- Fix problems with setting namespace field in the main config UI form

## [0.8.2] - 2022-05-29

### Changed
- Fix problem with completion models and components from controllers

## [0.8.1] - 2022-04-26

### Changed
- Update for PhpStorm 2022.1
- Map from camel-cased action names to underscored names for view files
- Fix handling of extension between CakePHP 3 and CakePHP 4.

## [0.8.0] - 2022-03-11

### Added
- Add completion for nested Cake 2 models
- Add completion for first argument of `find()` in Cake2 models

### Changed
- Fix warning when upgrading plugin to PhpStorm 2021.3.2
- Update build system to make upgrading plugin easier and improve QA
- Remove deprecated API usages

## [0.7.0] - 2021-08-09

### Added
- Add more support for Cake 4
- Add ClassRegistry::init() navigation to models
- Port to newer version of PhpStorm
- Fix bugs in plugin handling so it works now
- Add basic cake4 support

## [0.6.3] - 2020-12-17

### Changed
- Fix some problems with PhpStorm 2020.3

## [0.6.2] - 2020-05-04

### Changed
- Fix exception saving settings
- Revert some plugin UI changes that aren't ready yet

## [0.6.1] - 2020-05-02

### Changed
- Fix a bug in plugin.xml that caused plugin to be 
incompatible with IntellIJ Ultimate/PhpStorm 2020+.

## [0.6.0] - 2020-04-18

### Added
- Added plugin support for CakePHP 3
- Made Cake 2 and Cake 3 support individually toggleable per project.

## [0.5.0] - 2019-12-16

### Changed
- Fix failures related to settings changed interface on PhpStorm 2019.3
- Fix gutter cake icon to load view on cake2
- Fix line marker interface to not be so slow

## [0.4.0] - 2019-02-22

### Added
- Add support for view helpers and component in Cake 3

## [0.3.0] - 2018-09-01

### Added
- Add ControllerCompletionContributor that suggests models and components when you 
type `$this->` inside a controllers and adds models and components to the `$uses` 
and `$components` variables if a completion suggestion is accepted.
- Add a typeProvider for other view helpers when inside a view helper (still 
need to add a completion contributor for this to suggest the view helpers).
- Add a `ViewHelperCompletionContributor` for completing view helpers inside views

## [0.2.0] - 2017-11-05

### Added
- Fix navigation for projects with symlinks outside of project root
- Enable navigation/autocomplete for view helpers
- Better implementation of typeProvider3 interface that supports completion 
on values returned from dynamic properties

## [0.1.0] - 2017-09-04

### Added
- Initial release supporting CakePHP 2

[Unreleased]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.9.0...HEAD
[0.9.0]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.8.3...v0.9.0
[0.8.3]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.8.2...v0.8.3
[0.8.2]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.8.1...v0.8.2
[0.8.1]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.6.3...v0.7.0
[0.6.3]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.6.2...v0.6.3
[0.6.2]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.6.1...v0.6.2
[0.6.1]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/dmeybohm/chocolate-cakephp/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/dmeybohm/chocolate-cakephp/commits/v0.1.0
