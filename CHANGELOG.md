<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Chocolate CakePHP Changelog

## [Unreleased]

## [0.8.1] - 2022-04-26
### Changed
- Update for PhpStorm 2022.1
- Map from camelcased action names to underscored names for view files
- Set default template filename to '.php' when user selects view button

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
