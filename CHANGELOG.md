<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Chocolate CakePHP Changelog

## [Unreleasd]
### Added
- Add ability to create new view files from the controller
- Show pop-up menu in controller when multiple view files could be associated
- Add support for data views (JSON or XML by default) 
- Add floating Cake icon in views for navigating back to the controller
- Add shortcut `Ctrl-Alt-Up` for navigating from view back to controller
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
