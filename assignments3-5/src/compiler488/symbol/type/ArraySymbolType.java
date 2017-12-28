package compiler488.symbol.type;

public class ArraySymbolType extends SymbolType
{
    private final short lowerBound;
    private final short upperBound;

    private ScalarSymbolType elementType;

    public ArraySymbolType(short lowerBound, short upperBound, ScalarSymbolType elementType)
    {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.elementType = elementType;
    }

    public short getLowerBound()
    {
        return this.lowerBound;
    }

    public short getUpperBound()
    {
        return this.upperBound;
    }

    public void setElementType(ScalarSymbolType elementType)
    {
        this.elementType = elementType;
    }

    public SymbolType getElementType()
    {
        return this.elementType;
    }
}
