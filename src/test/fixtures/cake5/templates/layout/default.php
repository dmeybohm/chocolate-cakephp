<?php
echo $this->element('layout_header', ['siteName' => 'Chocolate', 'year' => 2026]);
echo $this->fetch('content');
