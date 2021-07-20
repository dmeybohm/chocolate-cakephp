### Planned for 0.8.0

- [ ] Autodetect cake and disable code unless forced
- [ ] Add "cell" support
- [ ] Support Cake 3 / Cake 4 models from controllers / cells  
- [ ] Use `expectedArguments` in `.phpstorm.meta.php` to provide completion for Cake classes, including:
    - [ ] Models
    - [ ] Breadcrumbs
    - [ ] Form
- [ ] Suppress dynamic property / method warning on classes that exist on Controllers / Cells / View Helpers / Views

### Planned for 0.9.0

- [ ] Add quickfix for undefined controller/view/component methods to add method
- [ ] Add error for undefined controller/view/component methods/fields
- [ ] Convert GUI to Kotlin
  - [ ] Add ability to add more paths to view navigation for Cake3 plugins
  - [ ] Use separate config sheet in preferences
- [ ] Rework plugin support to read from `vendor/cakephp-plugins.php`

### Planned for 1.0.0

- [ ] Investigate LineMarker performance
- [ ] Add support for navigating to view from `->render` methods
- [ ] Add menu to LineMarker to create view file

### Maybe in the future:

- [ ] Add completion contributor for view helpers inside view helpers
- [ ] Convert to PhpTypeProvider4 to do dynamic class lookups
- [ ] Disambiguate based on configuration code, for example `$this->initialize`
  in controller
