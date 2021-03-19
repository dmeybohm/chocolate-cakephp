### Planned for 0.7.0

- [ ] Rework plugin support to read from `vendor/cakephp-plugins.php`
- [ ] Support CakePhp 4 template paths  
- [ ] Add ability to add more paths to view navigation for Cake3 plugins
    - Use separate config sheet in preferences
- [ ] Make enabling of the projects/modules turned on by finding some cake-specific files

### Planned for 0.8.0

- [ ] Add typeProvider for Cake 3 table models 
- [ ] Add `->find` model autocompletion
- [ ] Add support for navigating to view from `->render` methods
- [ ] Add completion contributor for view helpers inside view helpers
- [ ] Add menu to LineMarker to create view file
- [ ] Investigate LineMarker performance

### Planned for 0.9.0

- [ ] Convert to PhpTypeProvider4 to do dynamic class lookups
- [ ] Disambiguate based on configuration code, for example `$this->initialize`
      in controller

### Planned for 1.0.0

- [ ] Add quickfix for undefined controller/view/component methods to add method
- [ ] Add error for undefined controller/view/component methods/fields