<h1>Movies by <?= h($artist['name']) ?>

<?= h($movie['description']) ?>
<?php

echo "<form>";
echo "<h4>Post a Comment</h4>";
echo $this->Form->textarea('comment');
echo $this->Form->button('Make a Comment');
echo $this->Form->checkbox('field', ['label' => 'Post anonymously']);
echo $this->Form->end();