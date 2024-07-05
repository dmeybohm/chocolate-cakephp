<?php

/** @var array $movies */

echo $this->Text->autoparagraph('Some amazing text...');

echo $this->Form->create();
echo $this->MovieFormatter->format($movies);
echo $this->Form->end();

$this->element('artist_element');