<?php
declare(strict_types=1);

namespace App\Model\Entity;

/**
 * An entity with every shape of accessor the plugin has to recognise or ignore.
 */
class Author extends BaseAuthor
{
    use AuthorAccessorsTrait;

    protected function _getFullName($value)
    {
        return trim($this->first_name . ' ' . $this->last_name);
    }

    protected function _getStoredValue($value)
    {
        return 'got:' . var_export($value, true);
    }

    protected function _getURLSlug()
    {
        return 'url-slug';
    }

    protected function _getName2()
    {
        return 'name-2';
    }

    public function _getPublicName()
    {
        return 'public-name';
    }

    private function _getSecret()
    {
        return 'secret';
    }

    protected function _getOverridden()
    {
        return 'child';
    }

    protected function _getNullable()
    {
        return null;
    }

    protected function _setNickname($value)
    {
        return strtoupper($value);
    }

    public function getNotAField()
    {
        return 'not-a-field';
    }
}
