### Planned for 0.6.0

- [ ] Make enabling of the projects/modules turned on by finding some cake-specific files
    - Make Cake 2 and Cake 3 support individually toggleable
    - use ProjectComponent to find some trigger files and set whether each is enabled by default,
      but only if user hasn't changed the default
- [ ] Add completion contributor for view helpers inside view helpers
- [ ] Add configurable navigation to components/models/views inside Cake plugins for 
      Cake 2 and Cake 3
- [ ] Add typeProvider for Cake 3 table models 

### Planned for 0.7.0

- [ ] Convert to PhpTypeProvider4 to do dynamic class lookups
- [ ] Add `->find` model autocompletion

### Planned for 0.8.0

- [ ] Add something to add models/components/view helpers automatically on first new use
- [ ] Add support for navigating to view from `->render` methods
- [ ] Add quickfix for undefined controller/view/component methods to add method
- [ ] Add error for undefined controller/view/component methods/fields
