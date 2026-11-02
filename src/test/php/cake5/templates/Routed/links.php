link:<?= $this->Html->link('x', ['action' => 'viewAll']) ?>

postLink:<?= $this->Form->postLink('x', ['action' => 'viewAll']) ?>

build:<?= $this->Url->build(['action' => 'viewAll']) ?>

create:<?= $this->Form->create(null, ['url' => ['action' => 'viewAll']]) ?>
