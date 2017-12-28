package compiler488.ast.expn;

import compiler488.ast.AST;
import compiler488.ast.Printable;
import compiler488.symbol.type.*;

/**
 * A placeholder for all expressions.
 */
public class Expn extends AST implements Printable
{
    private SymbolType type;

    public String toString()
    {
        return type.toString();
    }

    public SymbolType getEvalType()
    {
        return type;
    }

    public void setEvalType(SymbolType type)
    {
        this.type = type;
    }
}

