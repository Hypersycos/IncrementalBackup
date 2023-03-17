package net.hypersycos.incrementalbackup.util;

import java.util.Objects;

public final class Pair<X, Y>
{
    private final X first;
    private final Y second;

    public Pair(X first, Y second)
    {
        this.first = first;
        this.second = second;
    }

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

    public X first()
    {
        return first;
    }

    public Y second()
    {
        return second;
    }

    @Override
    public String toString()
    {
        return "Pair[" +
               "first=" + first + ", " +
               "second=" + second + ']';
    }

}
