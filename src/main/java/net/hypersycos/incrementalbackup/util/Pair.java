package net.hypersycos.incrementalbackup.util;

import java.util.Objects;

public record Pair<X,Y>(X first, Y second)
{
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(first, second);
    }
}
