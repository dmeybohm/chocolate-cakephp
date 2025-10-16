<?php

/** @var array $metadata */

echo $this->Text->autoparagraph('Custom nested template...');

echo $this->Form->create();
echo $this->MovieFormatter->format($metadata);
echo $this->Form->end();
