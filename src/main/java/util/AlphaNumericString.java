package util;

public class AlphaNumericString
{
    private String string;
    public AlphaNumericString(String string) throws IllegalArgumentException
    {
        if (!string.matches("[A-Za-z0-9]*")) throw new IllegalArgumentException(string+" is not alphanumeric.");
        this.string = string;
    }

    public String get()
    {
        return string;
    }
}
