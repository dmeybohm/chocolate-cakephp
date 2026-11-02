<?php

namespace App\Model\Entity;

/**
 * @property int $rating_count
 */
class Movie extends BaseMovie
{
    use MovieAccessorsTrait;

    public function someMovieMethod(): string
    {
        return "someMovieMethod";
    }

    protected function _getFullTitle(): string
    {
        return "full title";
    }

    /**
     * @return float
     */
    protected function _getRuntimeHours($value)
    {
        return $value / 60;
    }

    protected function _setTitle($title)
    {
        return trim($title);
    }

    protected function _getSlug(): string
    {
        return "slug";
    }

    protected function _setSlug($slug)
    {
        return strtolower($slug);
    }

    protected function _getRatingCount(): string
    {
        return "declared by the tag above";
    }

    private function _getSecret(): string
    {
        return "the framework never reaches this";
    }
}
