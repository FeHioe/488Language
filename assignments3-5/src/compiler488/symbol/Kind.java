package compiler488.symbol;

public enum Kind
{
    VAR ("variable"),
    FUNC ("function"),
    PROC ("procedure"),
    PARAM ("parameter"),
    UNKNOWN ("unknown");

    private String id;

    Kind(String id)
    {
        this.id = id;
    }

    @Override
    public String toString()
    {
        return this.id;
    }
}
