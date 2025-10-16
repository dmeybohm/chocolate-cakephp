# TODO

This list is outdated, and I have been using the issues on
[GitHub](https://github.com/dmeybohm/chocolate-cakephp/issues) instead.

Once I move these all there, I will delete this file.

### Planned for 0.8.0

- [x] Autodetect cake and disable code unless forced
  - [x] Autodetect app namespace unless specified
- [ ] Add "cell" support
- [ ] Support Cake 3 / Cake 4 models from controllers / cells  
- [ ] Use `expectedArguments` in `.phpstorm.meta.php` to provide completion for Cake classes, including:
    - [ ] Models
    - [ ] Breadcrumbs
    - [ ] Form

### Planned for 0.9.0

- [ ] Add quickfix for undefined controller/view/component methods to add method
- [ ] Suppress dynamic property / method warning on classes that exist on Controllers / Cells / View Helpers / Views
- [ ] Add error for undefined controller/view/component methods/fields
- [x] Add support for navigating to view from `->render` methods

### Planned for 1.0.0

- [ ] Convert GUI to Kotlin
  - [x] Add ability to add more paths to view navigation for Cake3 plugins
  - [x] Use separate config sheet in preferences
- [ ] Rework plugin support to read from `vendor/cakephp-plugins.php`
- [x] Investigate LineMarker performance
- [x] Add menu to LineMarker to create view file
  
### Maybe in the future:

- [x] Add completion contributor for view helpers inside view helpers
- [x] Convert to PhpTypeProvider4 to do dynamic class lookups
- [ ] Disambiguate based on configuration code, for example `$this->initialize`
  in controller
