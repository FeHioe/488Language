package compiler488.symbol.type;

public class CallableSymbolType extends SymbolType
{
    private final SymbolType returnType;
    private int numParameters;

    public CallableSymbolType(SymbolType returnType, int numParameters)
    {
        this.returnType = returnType;
        this.numParameters = numParameters;
    }

    public SymbolType getReturnType()
    {
        return this.returnType;
    }

    public int getNumParameters()
    {
        return this.numParameters;
    }

    public void setNumParameters(int numParameters)
    {
        this.numParameters = numParameters;
    }
}
