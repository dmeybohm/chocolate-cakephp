### Planned for 0.6.0

- [ ] Make enabling of the projects/modules turned on by finding some cake-specific files
    - Make Cake 2 and Cake 3 support individually toggleable
    - use ProjectComponent to find some trigger files and set whether each is enabled by default,
      but only if user hasn't changed the default
- [ ] Add configurable navigation to components/models/views inside Cake plugins for 
      Cake 2 and Cake 3

### Planned for 0.7.0

- [ ] Add completion contributor for view helpers inside view helpers
- [ ] Add `->find` model autocompletion

### Planned for 0.8.0

- [ ] Convert to PhpTypeProvider4 to do dynamic class lookups
- [ ] Add support for navigating to view from `->render` methods

### Planned for 0.9.0

- [ ] Add typeProvider for Cake 3 table models 
- [ ] Disambiguate based on configuration code, for example `$this->initialize`
      in controller

### Planned for 1.0.0

- [ ] Add quickfix for undefined controller/view/component methods to add method
- [ ] Add error for undefined controller/view/component methods/fields
